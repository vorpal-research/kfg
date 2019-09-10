package org.jetbrains.research.kfg.builder.cfg

import org.jetbrains.research.kfg.*
import org.jetbrains.research.kfg.analysis.IRVerifier
import org.jetbrains.research.kfg.ir.*
import org.jetbrains.research.kfg.ir.value.Slot
import org.jetbrains.research.kfg.ir.value.UsableValue
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.ValueUser
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.type.*
import org.jetbrains.research.kfg.util.DominatorTreeBuilder
import org.jetbrains.research.kfg.util.GraphTraversal
import org.jetbrains.research.kfg.util.print
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.JSRInlinerAdapter
import org.objectweb.asm.tree.*

private class LocalArray(private val locals: MutableMap<Int, Value> = hashMapOf())
    : ValueUser, MutableMap<Int, Value> by locals {
    override fun clear() {
        values.forEach { it.removeUser(this) }
        locals.clear()
    }

    override fun put(key: Int, value: Value): Value? {
        value.addUser(this)
        val prev = locals.put(key, value)
        prev?.removeUser(this)
        return prev
    }

    override fun putAll(from: Map<out Int, Value>) {
        from.forEach {
            put(it.key, it.value)
        }
    }

    override fun remove(key: Int): Value? {
        val res = locals.remove(key)
        res?.removeUser(this)
        return res
    }

    override fun replaceUsesOf(from: UsableValue, to: UsableValue) {
        entries.forEach { (key, value) ->
            if (value == from) {
                value.removeUser(this)
                locals[key] = to.get()
                to.addUser(this)
            }
        }
    }
}

private class FrameStack(private val stack: MutableList<Value> = mutableListOf()) : ValueUser, MutableList<Value> by stack {
    override fun replaceUsesOf(from: UsableValue, to: UsableValue) {
        stack.replaceAll { if (it == from) to.get() else it }
    }

    override fun add(element: Value): Boolean {
        element.addUser(this)
        return stack.add(element)
    }

    override fun add(index: Int, element: Value) {
        element.addUser(this)
        return stack.add(index, element)
    }

    override fun addAll(index: Int, elements: Collection<Value>): Boolean {
        elements.forEach { it.addUser(this) }
        return stack.addAll(index, elements)
    }

    override fun addAll(elements: Collection<Value>): Boolean {
        elements.forEach { it.addUser(this) }
        return stack.addAll(elements)
    }

    override fun clear() {
        stack.forEach { it.removeUser(this) }
        stack.clear()
    }

    override fun remove(element: Value): Boolean {
        stack.filter { it == element }.forEach { it.removeUser(this) }
        return stack.remove(element)
    }

    override fun removeAll(elements: Collection<Value>): Boolean {
        stack.filter { it in elements }.forEach { it.removeUser(this) }
        return stack.removeAll(elements)
    }

    override fun removeAt(index: Int): Value {
        val res = stack.removeAt(index)
        res.removeUser(this)
        return res
    }

    override fun retainAll(elements: Collection<Value>): Boolean {
        stack.filter { it !in elements }.forEach { it.removeUser(this) }
        return stack.retainAll(elements)
    }

    override fun set(index: Int, element: Value): Value {
        element.addUser(this)
        val res = stack.set(index, element)
        res.removeUser(this)
        return res
    }
}

private val AbstractInsnNode.isDebugNode
    get() = when (this) {
        is LineNumberNode -> true
        is FrameNode -> true
        else -> false
    }

private val AbstractInsnNode.isTerminate
    get() = when {
        this.isDebugNode -> false
        else -> isTerminateInst(this.opcode)
    }

private val AbstractInsnNode.throwsException
    get() = when {
        this.isDebugNode -> false
        else -> isExceptionThrowing(this.opcode)
    }

class CfgBuilder(val cm: ClassManager, val method: Method)
    : JSRInlinerAdapter(Opcodes.ASM5, method.mn, method.modifiers, method.name, method.asmDesc,
        method.mn.signature, method.exceptions.map { it.fullname }.toTypedArray()) {
    private val instFactory get() = cm.instruction
    val values get() = cm.value
    val types get() = cm.type

    private class StackFrame(val bb: BasicBlock) {
        val locals = LocalArray()
        val stack = FrameStack()

        val stackPhis = arrayListOf<PhiInst>()
        val localPhis = hashMapOf<Int, PhiInst>()
    }

    private val phiBlocks = hashSetOf<BasicBlock>()
    private val cycleEntries = hashSetOf<BasicBlock>()
    private val locals = LocalArray()
    private val nodeToBlock = hashMapOf<AbstractInsnNode, BasicBlock>()
    private val blockToNode = hashMapOf<BasicBlock, MutableList<AbstractInsnNode>>()
    private val frames = hashMapOf<BasicBlock, StackFrame>()
    private val stack = ArrayList<Value>()
    private var currentLocation = Location()

    private fun pop() = stack.removeAt(stack.lastIndex)
    private fun push(value: Value) = stack.add(value)
    private fun peek() = stack.last()

    private fun addInstruction(bb: BasicBlock, inst: Instruction) {
        inst.location = currentLocation
        bb += inst
    }

    private fun reserveState(bb: BasicBlock) {
        val sf = frames.getValue(bb)
        sf.stack.addAll(stack)
        sf.locals.putAll(locals)
        stack.clear()
        locals.clear()
    }

    private fun createStackPhis(bb: BasicBlock, predFrames: List<StackFrame>, size: Int) {
        for (index in 0 until size) {
            val incomings = predFrames.map { it.bb to it.stack[index] }.toMap()
            val incomingValues = incomings.values.toSet()
            when {
                incomingValues.size > 1 -> {
                    val newPhi = instFactory.getPhi(incomingValues.first().type, incomings)
                    addInstruction(bb, newPhi)
                    push(newPhi)
                }
                else -> push(incomingValues.first())
            }
        }
    }

    private fun createStackCyclePhis(sf: StackFrame, predFrames: List<StackFrame>, stackSize: Int) {
        val bb = sf.bb
        val stacks = predFrames.map { it.stack }
        for (indx in 0 until stackSize) {
            val type = stacks.filter { it.size > indx }.map { it[indx] }.first().type
            val phi = instFactory.getPhi(type, mapOf())
            addInstruction(bb, phi)
            push(phi)
            sf.stackPhis.add(phi as PhiInst)
        }
    }

    private fun createLocalPhis(bb: BasicBlock, predFrames: List<StackFrame>, definedLocals: Set<Int>) {
        for (local in definedLocals) {
            val incomings = predFrames.mapNotNull {
                val value = it.locals[local]
                when {
                    value != null -> it.bb to value
                    else -> null
                }
            }.toMap()

            if (incomings.size < predFrames.size) continue

            val incomingValues = incomings.values.toSet()
            when {
                incomingValues.size > 1 -> {
                    val type = mergeTypes(types, incomingValues.asSequence().map { it.type }.toSet())
                    if (type != null) {
                        val newPhi = instFactory.getPhi(type, incomings)
                        addInstruction(bb, newPhi)
                        locals[local] = newPhi
                    }
                }
                else -> locals[local] = incomingValues.first()
            }
        }
    }

    private fun createLocalCyclePhis(sf: StackFrame, predFrames: List<StackFrame>, definedLocals: Set<Int>) {
        val bb = sf.bb
        for (local in definedLocals) {
            val type = predFrames.mapNotNull { it.locals[local] }.first().type
            val phi = instFactory.getPhi(type, mapOf())
            addInstruction(bb, phi)
            locals[local] = phi
            sf.localPhis[local] = phi as PhiInst
        }
    }

    private fun recoverState(bb: BasicBlock) = when (bb) {
        is CatchBlock -> {
            val inst = instFactory.getCatch(bb.exception)
            addInstruction(bb, inst)
            push(inst)

            val predFrames = bb.getAllPredecessors().map { frames.getValue(it) }
            val definedLocals = predFrames.map { it.locals.keys }.flatten().toSet()
            when (bb) {
                in cycleEntries -> createLocalCyclePhis(frames.getValue(bb), predFrames, definedLocals)
                else -> createLocalPhis(bb, predFrames, definedLocals)
            }

        }
        in phiBlocks -> {
            val sf = frames.getValue(bb)
            val predFrames = bb.predecessors.map { frames.getValue(it) }
            val stacks = predFrames.map { it.stack }
            val stackSizes = stacks.asSequence().map { it.size }.toSet()

            when (bb) {
                in cycleEntries -> {
                    require(stackSizes.size <= 2) { "Stack sizes of ${bb.name} predecessors are different" }

                    val stackSize = stackSizes.max()!!
                    createStackCyclePhis(sf, predFrames, stackSize)

                    val definedLocals = predFrames.map { it.locals.keys }.flatten().toSet()
                    createLocalCyclePhis(sf, predFrames, definedLocals)

                }
                else -> {
                    require(stackSizes.size == 1) { "Stack sizes of ${bb.name} predecessors are different" }
                    createStackPhis(bb, predFrames, stackSizes.first())

                    val definedLocals = predFrames.map { it.locals.keys }.flatten().toSet()
                    createLocalPhis(bb, predFrames, definedLocals)
                }
            }
        }
        else -> {
            val predFrame = bb.predecessors.asSequence().map { frames.getValue(it) }.firstOrNull()
            predFrame?.stack?.forEach { push(it) }
            predFrame?.locals?.forEach { local, value -> locals[local] = value }
        }
    }

    private fun convertConst(insn: InsnNode) {
        val opcode = insn.opcode
        val cnst = when (opcode) {
            ACONST_NULL -> values.getNullConstant()
            ICONST_M1 -> values.getIntConstant(-1)
            in ICONST_0..ICONST_5 -> values.getIntConstant(opcode - ICONST_0)
            in LCONST_0..LCONST_1 -> values.getLongConstant((opcode - LCONST_0).toLong())
            in FCONST_0..FCONST_2 -> values.getFloatConstant((opcode - FCONST_0).toFloat())
            in DCONST_0..DCONST_1 -> values.getDoubleConstant((opcode - DCONST_0).toDouble())
            else -> throw InvalidOpcodeError("Unknown const $opcode")
        }
        push(cnst)
    }

    private fun convertArrayLoad(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val index = pop()
        val arrayRef = pop()
        val inst = instFactory.getArrayLoad(arrayRef, index)
        addInstruction(bb, inst)
        push(inst)
    }

    private fun convertArrayStore(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val value = pop()
        val index = pop()
        val array = pop()
        addInstruction(bb, instFactory.getArrayStore(array, index, value))
    }

    private fun convertPop(insn: InsnNode) {
        when (val opcode = insn.opcode) {
            POP -> pop()
            POP2 -> {
                val top = pop()
                if (!top.type.isDWord) pop()
            }
            else -> throw InvalidOpcodeError("Pop opcode $opcode")
        }
    }

    private fun convertDup(insn: InsnNode) {
        when (val opcode = insn.opcode) {
            DUP -> push(peek())
            DUP_X1 -> {
                val top = pop()
                val prev = pop()
                push(top)
                push(prev)
                push(top)
            }
            DUP_X2 -> {
                val val1 = pop()
                val val2 = pop()
                if (val2.type.isDWord) {
                    push(val1)
                    push(val2)
                    push(val1)
                } else {
                    val val3 = pop()
                    push(val1)
                    push(val3)
                    push(val2)
                    push(val1)
                }
            }
            DUP2 -> {
                val top = pop()
                if (top.type.isDWord) {
                    push(top)
                    push(top)
                } else {
                    val bot = pop()
                    push(bot)
                    push(top)
                    push(bot)
                    push(top)
                }
            }
            DUP2_X1 -> {
                val val1 = pop()
                if (val1.type.isDWord) {
                    val val2 = pop()
                    push(val1)
                    push(val2)
                    push(val1)
                } else {
                    val val2 = pop()
                    val val3 = pop()
                    push(val2)
                    push(val1)
                    push(val3)
                    push(val2)
                    push(val1)
                }
            }
            DUP2_X2 -> {
                val val1 = pop()
                if (val1.type.isDWord) {
                    val val2 = pop()
                    if (val2.type.isDWord) {
                        push(val1)
                        push(val2)
                        push(val1)
                    } else {
                        val val3 = pop()
                        push(val1)
                        push(val3)
                        push(val2)
                        push(val1)
                    }
                } else {
                    val val2 = pop()
                    val val3 = pop()
                    if (val3.type.isDWord) {
                        push(val2)
                        push(val1)
                        push(val3)
                        push(val2)
                        push(val1)
                    } else {
                        val val4 = pop()
                        push(val2)
                        push(val1)
                        push(val4)
                        push(val3)
                        push(val2)
                        push(val1)
                    }
                }
            }
            else -> throw InvalidOpcodeError("Dup opcode $opcode")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun convertSwap(insn: InsnNode) {
        val top = pop()
        val bot = pop()
        push(top)
        push(bot)
    }

    private fun convertBinary(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val rhv = pop()
        val lhv = pop()
        val binOp = toBinaryOpcode(insn.opcode)
        val inst = instFactory.getBinary(binOp, lhv, rhv)
        addInstruction(bb, inst)
        push(inst)
    }

    private fun convertUnary(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val operand = pop()
        val op = when (insn.opcode) {
            in INEG..DNEG -> UnaryOpcode.NEG
            ARRAYLENGTH -> UnaryOpcode.LENGTH
            else -> throw InvalidOpcodeError("Unary opcode ${insn.opcode}")
        }
        val inst = instFactory.getUnary(op, operand)
        addInstruction(bb, inst)
        push(inst)
    }

    private fun convertCast(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val op = pop()
        val type = when (insn.opcode) {
            in arrayOf(I2L, F2L, D2L) -> types.longType
            in arrayOf(I2F, L2F, D2F) -> types.floatType
            in arrayOf(I2D, L2D, F2D) -> types.doubleType
            in arrayOf(L2I, F2I, D2I) -> types.intType
            I2B -> types.byteType
            I2C -> types.charType
            I2S -> types.shortType
            else -> throw InvalidOpcodeError("Cast opcode ${insn.opcode}")
        }
        val inst = instFactory.getCast(type, op)
        addInstruction(bb, inst)
        push(inst)
    }

    private fun convertCmp(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val lhv = pop()
        val rhv = pop()
        val op = toCmpOpcode(insn.opcode)
        val resType = getCmpResultType(types, op)
        val inst = instFactory.getCmp(resType, op, lhv, rhv)
        addInstruction(bb, inst)
        push(inst)
    }

    private fun convertReturn(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        when (RETURN) {
            insn.opcode -> addInstruction(bb, instFactory.getReturn())
            else -> {
                val retval = pop()
                addInstruction(bb, instFactory.getReturn(retval))
            }
        }
    }

    private fun convertMonitor(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val owner = pop()
        when (insn.opcode) {
            MONITORENTER -> addInstruction(bb, instFactory.getEnterMonitor(owner))
            MONITOREXIT -> addInstruction(bb, instFactory.getExitMonitor(owner))
            else -> throw InvalidOpcodeError("Monitor opcode ${insn.opcode}")
        }
    }

    private fun convertThrow(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val throwable = pop()
        addInstruction(bb, instFactory.getThrow(throwable))
    }

    private fun convertLocalLoad(insn: VarInsnNode) {
        push(locals[insn.`var`]!!)
    }

    private fun convertLocalStore(insn: VarInsnNode) {
        locals[insn.`var`] = pop()
    }

    private fun convertInsn(insn: InsnNode) {
        when (insn.opcode) {
            NOP -> Unit
            in ACONST_NULL..DCONST_1 -> convertConst(insn)
            in IALOAD..SALOAD -> convertArrayLoad(insn)
            in IASTORE..SASTORE -> convertArrayStore(insn)
            in POP..POP2 -> convertPop(insn)
            in DUP..DUP2_X2 -> convertDup(insn)
            SWAP -> convertSwap(insn)
            in IADD..DREM -> convertBinary(insn)
            in INEG..DNEG -> convertUnary(insn)
            in ISHL..LXOR -> convertBinary(insn)
            in I2L..I2S -> convertCast(insn)
            in LCMP..DCMPG -> convertCmp(insn)
            in IRETURN..RETURN -> convertReturn(insn)
            ARRAYLENGTH -> convertUnary(insn)
            ATHROW -> convertThrow(insn)
            in MONITORENTER..MONITOREXIT -> convertMonitor(insn)
            else -> throw InvalidOpcodeError("Insn opcode ${insn.opcode}")
        }
    }

    private fun convertIntInsn(insn: IntInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val opcode = insn.opcode
        val operand = insn.operand
        when (opcode) {
            BIPUSH -> push(values.getIntConstant(operand))
            SIPUSH -> push(values.getIntConstant(operand))
            NEWARRAY -> {
                val type = parsePrimaryType(types, operand)
                val count = pop()
                val inst = instFactory.getNewArray(type, count)
                addInstruction(bb, inst)
                push(inst)
            }
            else -> throw InvalidOpcodeError("IntInsn opcode $opcode")
        }
    }

    private fun convertVarInsn(insn: VarInsnNode) {
        when (insn.opcode) {
            in ISTORE..ASTORE -> convertLocalStore(insn)
            in ILOAD..ALOAD -> convertLocalLoad(insn)
            RET -> throw UnsupportedOperation("Opcode `RET`")
            else -> throw InvalidOpcodeError("VarInsn opcode ${insn.opcode}")
        }
    }

    private fun convertTypeInsn(insn: TypeInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val opcode = insn.opcode
        val type = try {
            parseDesc(types, insn.desc)
        } catch (e: InvalidTypeDescError) {
            types.getRefType(insn.desc)
        }
        when (opcode) {
            NEW -> {
                val inst = instFactory.getNew(type)
                addInstruction(bb, inst)
                push(inst)
            }
            ANEWARRAY -> {
                val count = pop()
                val inst = instFactory.getNewArray(type, count)
                addInstruction(bb, inst)
                push(inst)
            }
            CHECKCAST -> {
                val castable = pop()
                val inst = instFactory.getCast(type, castable)
                addInstruction(bb, inst)
                push(inst)
            }
            INSTANCEOF -> {
                val obj = pop()
                val inst = instFactory.getInstanceOf(type, obj)
                addInstruction(bb, inst)
                push(inst)
            }
            else -> throw InvalidOpcodeError("$opcode in TypeInsn")
        }
    }

    private fun convertFieldInsn(insn: FieldInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val opcode = insn.opcode
        val fieldType = parseDesc(types, insn.desc)
        val `class` = cm.getByName(insn.owner)
        when (opcode) {
            GETSTATIC -> {
                val field = `class`.getField(insn.name, fieldType)
                val inst = instFactory.getFieldLoad(field)
                addInstruction(bb, inst)
                push(inst)
            }
            PUTSTATIC -> {
                val field = `class`.getField(insn.name, fieldType)
                val value = pop()
                addInstruction(bb, instFactory.getFieldStore(field, value))
            }
            GETFIELD -> {
                val field = `class`.getField(insn.name, fieldType)
                val owner = pop()
                val inst = instFactory.getFieldLoad(owner, field)
                addInstruction(bb, inst)
                push(inst)
            }
            PUTFIELD -> {
                val value = pop()
                val owner = pop()
                val field = `class`.getField(insn.name, fieldType)
                addInstruction(bb, instFactory.getFieldStore(owner, field, value))
            }
        }
    }

    private fun convertMethodInsn(insn: MethodInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val `class` = when {
            // FIXME: This is literally fucked up. If insn owner is an array, class of the CallInst should be java/lang/Object,
            //  because java array don't have their own methods
            insn.owner.startsWith("[") -> cm.getByName(TypeFactory.objectClass)
            else -> cm.getByName(insn.owner)
        }
        val method = `class`.getMethod(insn.name, insn.desc)
        val args = arrayListOf<Value>()
        method.argTypes.forEach { _ -> args.add(0, pop()) }

        val isNamed = !method.returnType.isVoid
        val opcode = toCallOpcode(insn.opcode)
        val call = when (insn.opcode) {
            INVOKESTATIC -> instFactory.getCall(opcode, method, `class`, args.toTypedArray(), isNamed)
            in arrayOf(INVOKEVIRTUAL, INVOKESPECIAL, INVOKEINTERFACE) -> {
                val obj = pop()
                instFactory.getCall(opcode, method, `class`, obj, args.toTypedArray(), isNamed)
            }
            else -> throw InvalidOpcodeError("Method insn opcode ${insn.opcode}")
        }
        addInstruction(bb, call)
        if (isNamed) {
            push(call)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun convertInvokeDynamicInsn(insn: InvokeDynamicInsnNode): Unit = throw UnsupportedOperation("InvokeDynamicInsn")

    private fun convertJumpInsn(insn: JumpInsnNode) {
        val bb = nodeToBlock.getValue(insn)

        when (GOTO) {
            insn.opcode -> {
                val trueSuccessor = nodeToBlock.getValue(insn.label)
                addInstruction(bb, instFactory.getJump(trueSuccessor))
            }
            else -> {
                val falseSuccessor = nodeToBlock.getValue(insn.next)
                val trueSuccessor = nodeToBlock.getValue(insn.label)
                val name = Slot()
                val rhv = pop()
                val opc = toCmpOpcode(insn.opcode)
                val resType = getCmpResultType(types, opc)
                val cond = when (insn.opcode) {
                    in IFEQ..IFLE -> instFactory.getCmp(name, resType, opc, rhv, values.getZeroConstant(rhv.type))
                    in IF_ICMPEQ..IF_ACMPNE -> instFactory.getCmp(name, resType, opc, pop(), rhv)
                    in IFNULL..IFNONNULL -> instFactory.getCmp(name, resType, opc, rhv, values.getNullConstant())
                    else -> throw InvalidOpcodeError("Jump opcode ${insn.opcode}")
                }
                addInstruction(bb, cond)
                val castedCond = if (cond.type is BoolType) cond else instFactory.getCast(types.boolType, cond)
                addInstruction(bb, instFactory.getBranch(castedCond, trueSuccessor, falseSuccessor))
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun convertLabel(lbl: LabelNode) = Unit

    private fun convertLdcInsn(insn: LdcInsnNode) {
        when (val cst = insn.cst) {
            is Int -> push(values.getIntConstant(cst))
            is Float -> push(values.getFloatConstant(cst))
            is Double -> push(values.getDoubleConstant(cst))
            is Long -> push(values.getLongConstant(cst))
            is String -> push(values.getStringConstant(cst))
            is org.objectweb.asm.Type -> {
                val klass = when (val temp = parseDesc(types, cst.descriptor)) {
                    is ClassType -> temp.`class`
                    else -> cm.getByName("$temp")
                }
                push(values.getClassConstant(klass))
            }
            is org.objectweb.asm.Handle -> {
                val `class` = cm.getByName(cst.owner)
                val method = `class`.getMethod(cst.name, cst.desc)
                push(values.getMethodConstant(method))
            }
            else -> throw InvalidOperandError("Unknown object $cst")
        }
    }

    private fun convertIincInsn(insn: IincInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val lhv = locals[insn.`var`] ?: throw InvalidOperandError("${insn.`var`} local is invalid")
        val rhv = instFactory.getBinary(BinaryOpcode.Add(), values.getIntConstant(insn.incr), lhv)
        locals[insn.`var`] = rhv
        addInstruction(bb, rhv)
    }

    private fun convertTableSwitchInsn(insn: TableSwitchInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val index = pop()
        val min = values.getIntConstant(insn.min)
        val max = values.getIntConstant(insn.max)
        val default = nodeToBlock.getValue(insn.dflt)
        val branches = insn.labels.map { nodeToBlock.getValue(it as AbstractInsnNode) }.toTypedArray()
        addInstruction(bb, instFactory.getTableSwitch(index, min, max, default, branches))
    }

    private fun convertLookupSwitchInsn(insn: LookupSwitchInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val default = nodeToBlock.getValue(insn.dflt)
        val branches = hashMapOf<Value, BasicBlock>()
        val key = pop()
        for (i in 0..insn.keys.lastIndex) {
            branches[values.getIntConstant(insn.keys[i] as Int)] = nodeToBlock.getValue(insn.labels[i] as LabelNode)
        }
        addInstruction(bb, instFactory.getSwitch(key, default, branches))
    }

    private fun convertMultiANewArrayInsn(insn: MultiANewArrayInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        super.visitMultiANewArrayInsn(insn.desc, insn.dims)
        val dimensions = arrayListOf<Value>()
        for (it in 0 until insn.dims) dimensions.add(pop())
        val type = parseDesc(types, insn.desc)
        val inst = instFactory.getNewArray(type, dimensions.toTypedArray())
        addInstruction(bb, inst)
        push(inst)
    }

    private fun convertLineNumber(insn: LineNumberNode) {
        val `package` = method.`class`.`package`
        val file = method.`class`.cn.sourceFile ?: "Unknown"
        val line = insn.line
        currentLocation = Location(`package`, file, line)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun convertFrame(inst: FrameNode) = Unit

    @Suppress("UNCHECKED_CAST")
    private fun buildCFG() {
        val tryCatchBlocks = method.mn.tryCatchBlocks as MutableList<TryCatchBlockNode>
        for (insn in tryCatchBlocks) {
            val type = when {
                insn.type != null -> types.getRefType(insn.type)
                else -> types.getRefType(CatchBlock.defaultException)
            }
            nodeToBlock[insn.handler] = CatchBlock("catch", type)
        }

        var bb: BasicBlock = BodyBlock("bb")
        var insnList = blockToNode.getOrPut(bb, ::arrayListOf)

        for (insn in method.mn.instructions) {
            if (insn is LabelNode) {
                when {
                    insn.next == null -> Unit
                    insn.previous == null -> {
                        // register entry block if first insn of method is label
                        bb = nodeToBlock.getOrPut(insn) { bb }

                        val entry = BodyBlock("entry")
                        addInstruction(entry, instFactory.getJump(bb))
                        entry.addSuccessor(bb)
                        blockToNode[entry] = arrayListOf()
                        bb.addPredecessor(entry)

                        method.add(entry)
                    }
                    else -> {
                        bb = nodeToBlock.getOrPut(insn) { BodyBlock("label") }
                        insnList = blockToNode.getOrPut(bb, ::arrayListOf)

                        if (!insn.previous.isTerminate) {
                            val prev = nodeToBlock.getValue(insn.previous)
                            bb.addPredecessor(prev)
                            prev.addSuccessor(bb)
                        }
                    }
                }
            } else {
                bb = nodeToBlock.getOrPut(insn as AbstractInsnNode) { bb }
                insnList = blockToNode.getOrPut(bb, ::arrayListOf)

                when (insn) {
                    is JumpInsnNode -> {
                        if (insn.opcode != GOTO) {
                            val falseSuccessor = nodeToBlock.getOrPut(insn.next) { BodyBlock("if.else") }
                            bb.addSuccessor(falseSuccessor)
                            falseSuccessor.addPredecessor(bb)
                        }
                        val trueSuccName = if (insn.opcode == GOTO) "goto" else "if.then"
                        val trueSuccessor = nodeToBlock.getOrPut(insn.label) { BodyBlock(trueSuccName) }
                        bb.addSuccessor(trueSuccessor)
                        trueSuccessor.addPredecessor(bb)
                    }
                    is TableSwitchInsnNode -> {
                        val default = nodeToBlock.getOrPut(insn.dflt) { BodyBlock("tableswitch.default") }
                        bb.addSuccessors(default)
                        default.addPredecessor(bb)

                        val labels = insn.labels as MutableList<LabelNode>
                        for (lbl in labels) {
                            val lblBB = nodeToBlock.getOrPut(lbl) { BodyBlock("tableswitch") }
                            bb.addSuccessors(lblBB)
                            lblBB.addPredecessor(bb)
                        }
                    }
                    is LookupSwitchInsnNode -> {
                        val default = nodeToBlock.getOrPut(insn.dflt) { BodyBlock("switch.default") }
                        bb.addSuccessors(default)
                        default.addPredecessor(bb)

                        val labels = insn.labels as MutableList<LabelNode>
                        for (lbl in labels) {
                            val lblBB = nodeToBlock.getOrPut(lbl) { BodyBlock("switch") }
                            bb.addSuccessors(lblBB)
                            lblBB.addPredecessor(bb)
                        }
                    }
                    else -> {
                        if (insn.throwsException && (insn.next != null)) {
                            val next = nodeToBlock.getOrPut(insn.next) { BodyBlock("bb") }
                            if (!insn.isTerminate) {
                                bb.addSuccessor(next)
                                next.addPredecessor(bb)
                            }
                        }
                    }
                }
            }
            insnList.add(insn as AbstractInsnNode)
            method.add(bb)
        }
        for (insn in tryCatchBlocks) {
            val handle = nodeToBlock.getValue(insn.handler) as CatchBlock
            nodeToBlock[insn.handler] = handle
            var cur = insn.start as AbstractInsnNode

            var thrower = nodeToBlock.getValue(cur)
            val throwers = arrayListOf<BasicBlock>()
            while (cur != insn.end) {
                bb = nodeToBlock.getValue(cur)
                if (bb.name != thrower.name) {
                    throwers.add(thrower)
                    thrower.addHandler(handle)
                    thrower = bb
                }
                cur = cur.next
            }

            if (throwers.isEmpty()) throwers.add(thrower)
            handle.addThrowers(throwers)
            method.addCatchBlock(handle)
        }
    }

    private fun buildFrames() {
        val sf = frames.getOrPut(method.entry) { StackFrame(method.entry) }
        sf.locals.putAll(locals)

        for (bb in method.basicBlocks.drop(1)) {
            frames.getOrPut(bb) { StackFrame(bb) }
        }
    }

    private fun buildPhiBlocks() {
        val dominatorTree = DominatorTreeBuilder(method.basicBlocks.toSet()).build()

        for ((bb, dtn) in dominatorTree) {
            val preds = bb.predecessors
            if (preds.size > 1) {
                for (pred in preds) {
                    var runner: BasicBlock? = pred
                    while (runner != null && runner != dtn.idom?.value) {
                        phiBlocks.add(bb)
                        runner = dominatorTree.getValue(runner).idom?.value
                    }
                }
            }
        }
    }

    private fun recoverStackPhis(sf: StackFrame, predFrames: List<StackFrame>) {
        val bb = sf.bb
        for ((indx, phi) in sf.stackPhis.withIndex()) {
            val incomings = predFrames.map { it.bb to it.stack[indx] }.toMap()
            val incomingValues = incomings.values.toSet()
            when {
                incomingValues.size > 1 -> {
                    val newPhi = instFactory.getPhi(phi.name, phi.type, incomings)
                    phi.replaceAllUsesWith(newPhi)
                    bb.replace(phi, newPhi)
                }
                else -> {
                    phi.replaceAllUsesWith(incomingValues.first())
                    bb -= phi
                }
            }
            phi.operands.forEach { it.removeUser(phi) }
        }
    }

    private fun recoverLocalPhis(sf: StackFrame, predFrames: List<StackFrame>): Set<PhiInst> {
        val removablePhis = hashSetOf<PhiInst>()
        val bb = sf.bb
        for ((local, phi) in sf.localPhis) {
            val incomings = predFrames.mapNotNull {
                val value = it.locals[local]
                when {
                    value != null -> it.bb to value
                    else -> null
                }
            }.toMap()

            if (incomings.size < predFrames.size) {
                removablePhis.add(phi)
                continue
            }

            val incomingValues = incomings.values.toSet()
            when {
                incomingValues.size > 1 -> {
                    val type = mergeTypes(types, incomingValues.asSequence().map { it.type }.toSet())
                    when (type) {
                        null -> removablePhis.add(phi)
                        else -> {
                            val newPhi = instFactory.getPhi(phi.name, type, incomings)
                            phi.replaceAllUsesWith(newPhi)
                            phi.operands.forEach { it.removeUser(phi) }
                            bb.replace(phi, newPhi)
                        }
                    }
                }
                else -> {
                    phi.replaceAllUsesWith(incomingValues.first())
                    phi.operands.forEach { it.removeUser(phi) }
                    bb -= phi
                }
            }
        }
        return removablePhis
    }

    private fun buildPhiInstructions() {
        val removablePhis = hashSetOf<PhiInst>()
        val processPhis = arrayListOf<PhiInst>()

        for ((bb, sf) in frames) {
            if (bb !in phiBlocks && bb !in cycleEntries) continue

            val predFrames = when (bb) {
                is CatchBlock -> bb.getAllPredecessors()
                else -> bb.predecessors
            }.map { frames.getValue(it) }
            recoverStackPhis(sf, predFrames)

            val removable = recoverLocalPhis(sf, predFrames)
            removablePhis.addAll(removable)
        }

        for (inst in method.flatten().mapNotNull { it as? PhiInst }) {
            val incomings = inst.incomingValues
            val instUsers = inst.users.mapNotNull { it as? Instruction }
            when {
                instUsers.isEmpty() -> removablePhis.add(inst)
                instUsers.size == 1 && instUsers.first() == inst -> removablePhis.add(inst)
                incomings.size == 2 && incomings.contains(inst) -> {
                    if (incomings.first() == inst) inst.replaceAllUsesWith(incomings.last())
                    else inst.replaceAllUsesWith(incomings.first())
                    inst.operands.forEach { op -> op.removeUser(inst) }
                    instUsers.mapNotNull { it as? PhiInst }.forEach { processPhis.add(it) }
                }
            }
        }

        processPhis.addAll(removablePhis)
        while (processPhis.isNotEmpty()) {
            val top = processPhis.first()
            processPhis.removeAt(0)
            val incomings = top.incomingValues
            val incomingsSet = incomings.toSet()
            val instUsers = top.users.mapNotNull { it as? Instruction }
            val operands = top.operands

            if (incomingsSet.size == 1) {
                val first = incomingsSet.first()
                top.replaceAllUsesWith(first)
                operands.forEach { it.removeUser(top) }
                if (first is PhiInst) processPhis.add(first)
                top.parent?.remove(top) ?: continue
                operands.mapNotNull { it as? PhiInst }.forEach { processPhis.add(it) }

            } else if (incomings.size == 2 && incomings.contains(top)) {
                if (incomings.first() == top) top.replaceAllUsesWith(incomings.last())
                else top.replaceAllUsesWith(incomings.first())
                operands.forEach { it.removeUser(top) }
                instUsers.mapNotNull { it as? PhiInst }.forEach { processPhis.add(it) }

            } else if (instUsers.isEmpty()) {
                operands.forEach { it.removeUser(top) }
                top.parent?.remove(top) ?: continue
                operands.mapNotNull { it as? PhiInst }.forEach { processPhis.add(it) }

            } else if (instUsers.size == 1 && instUsers.first() == top) {
                operands.forEach { it.removeUser(top) }
                top.parent?.remove(top)
                operands.asSequence().mapNotNull { it as? PhiInst }
                        .mapNotNull { if (it == top) null else it }.toList()
                        .forEach { processPhis.add(it) }

            } else if (removablePhis.containsAll(instUsers)) {
                operands.forEach { it.removeUser(top) }
                top.parent?.remove(top) ?: continue
                operands.mapNotNull { it as? PhiInst }.forEach { processPhis.add(it) }
            }
        }

        removablePhis.forEach { phi ->
            val instUsers = phi.users.mapNotNull { it as? Instruction }
            val methodInstUsers = instUsers.mapNotNull { if (it.parent != null) it else null }
            require(methodInstUsers.isEmpty()) { "Instruction ${phi.print()} still have usages" }
            phi.parent?.remove(phi)
        }
    }

    fun build(): Method {
        var localIndx = 0
        if (!method.isStatic) {
            val `this` = values.getThis(types.getRefType(method.`class`))
            locals[localIndx++] = `this`
            method.slottracker.addValue(`this`)
        }
        for ((indx, type) in method.argTypes.withIndex()) {
            val arg = values.getArgument(indx, method, type)
            locals[localIndx] = arg
            if (type.isDWord) localIndx += 2
            else ++localIndx
            method.slottracker.addValue(arg)
        }

        buildCFG()  // build basic blocks graph

        if (method.isEmpty()) {
            // if method does not contain any code, we can't build cfg for it
            return method
        }

        buildPhiBlocks() // find out to which bb we should insert phi insts using dominator tree
        buildFrames() // build frame maps for each basic block

        method.catchEntries.forEach { cb -> cb.getAllPredecessors().forEach { it.addSuccessor(cb) } }
        val (order, c) = GraphTraversal(method).topologicalSort()
        cycleEntries.addAll(c)
        method.catchEntries.forEach { cb -> cb.getAllPredecessors().forEach { it.removeSuccessor(cb) } }

        for (bb in order.reversed()) {
            recoverState(bb)
            for (insn in blockToNode.getValue(bb)) {
                when (insn) {
                    is InsnNode -> convertInsn(insn)
                    is IntInsnNode -> convertIntInsn(insn)
                    is VarInsnNode -> convertVarInsn(insn)
                    is TypeInsnNode -> convertTypeInsn(insn)
                    is FieldInsnNode -> convertFieldInsn(insn)
                    is MethodInsnNode -> convertMethodInsn(insn)
                    is InvokeDynamicInsnNode -> convertInvokeDynamicInsn(insn)
                    is JumpInsnNode -> convertJumpInsn(insn)
                    is LabelNode -> convertLabel(insn)
                    is LdcInsnNode -> convertLdcInsn(insn)
                    is IincInsnNode -> convertIincInsn(insn)
                    is TableSwitchInsnNode -> convertTableSwitchInsn(insn)
                    is LookupSwitchInsnNode -> convertLookupSwitchInsn(insn)
                    is MultiANewArrayInsnNode -> convertMultiANewArrayInsn(insn)
                    is LineNumberNode -> convertLineNumber(insn)
                    is FrameNode -> convertFrame(insn)
                    else -> throw InvalidOpcodeError("Unknown insn: ${insn.print()}")
                }
            }
            val last = bb.instructions.lastOrNull()
            if (last == null || !last.isTerminate) {
                require(bb.successors.size == 1)
                addInstruction(bb, instFactory.getJump(bb.successors.first()))
            }

            reserveState(bb)
        }

        buildPhiInstructions()
        RetvalBuilder(cm).visit(method)
        CfgOptimizer(cm).visit(method)
//        BoolValueAdapter.visit(method)

        // this is fucked up, but sometimes there are really empty blocks in bytecode
        nodeToBlock.values.forEach {
            if (it.isEmpty && it.predecessors.isEmpty() && it.successors.isEmpty()) {
                method.remove(it)
            }
        }

        method.slottracker.rerun()
        IRVerifier(cm).visit(method)

        return method
    }
}