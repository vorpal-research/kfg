package org.jetbrains.research.kfg.builder.cfg

import org.jetbrains.research.kfg.*
import org.jetbrains.research.kfg.ir.*
import org.jetbrains.research.kfg.ir.value.Slot
import org.jetbrains.research.kfg.ir.value.UsableValue
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.ValueUser
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.type.*
import org.jetbrains.research.kfg.util.DominatorTreeBuilder
import org.jetbrains.research.kfg.util.TopologicalSorter
import org.jetbrains.research.kfg.util.print
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.JSRInlinerAdapter
import org.objectweb.asm.tree.*
import java.util.*

class LocalArray(private val locals: MutableMap<Int, Value> = hashMapOf())
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

class FrameStack(private val stack: MutableList<Value> = mutableListOf()) : ValueUser, MutableList<Value> by stack {
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

class CfgBuilder(val method: Method)
    : JSRInlinerAdapter(Opcodes.ASM5, method.mn, method.modifiers, method.name, method.getAsmDesc(),
        method.mn.signature, method.exceptions.map { it.getFullname() }.toTypedArray()) {

    inner class StackFrame(val bb: BasicBlock) {
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
    private val stack = Stack<Value>()
    private var currentLocation = Location()

    private fun addInstruction(bb: BasicBlock, inst: Instruction) {
        inst.location = currentLocation
        bb.addInstruction(inst)
    }

    private fun reserveState(bb: BasicBlock) {
        val sf = frames.getValue(bb)
        sf.stack.addAll(stack)
        sf.locals.putAll(locals)
        stack.clear()
        locals.clear()
    }

    private fun createStackPhis(bb: BasicBlock, predFrames: List<StackFrame>, size: Int) {
        for (indx in 0 until size) {
            val incomings = predFrames.map { it.bb to it.stack[indx] }.toMap()
            val incomingValues = incomings.values.toSet()
            when {
                incomingValues.size > 1 -> {
                    val newPhi = IF.getPhi(incomingValues.first().type, incomings)
                    addInstruction(bb, newPhi)
                    stack.push(newPhi)
                }
                else -> stack.push(incomingValues.first())
            }
        }
    }

    private fun createStackCyclePhis(sf: StackFrame, predFrames: List<StackFrame>, stackSize: Int) {
        val bb = sf.bb
        val stacks = predFrames.map { it.stack }
        for (indx in 0 until stackSize) {
            val type = stacks.filter { it.size > indx }.map { it[indx] }.first().type
            val phi = IF.getPhi(type, mapOf())
            addInstruction(bb, phi)
            stack.push(phi)
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
                    val type = mergeTypes(incomingValues.map { it.type }.toSet())
                    if (type != null) {
                        val newPhi = IF.getPhi(type, incomings)
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
            val phi = IF.getPhi(type, mapOf())
            addInstruction(bb, phi)
            locals[local] = phi
            sf.localPhis[local] = phi as PhiInst
        }
    }

    private fun recoverState(bb: BasicBlock) = when (bb) {
        is CatchBlock -> {
            val inst = IF.getCatch(bb.exception)
            addInstruction(bb, inst)
            stack.push(inst)

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
            val stackSizes = stacks.map { it.size }.toSet()

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
            val predFrame = bb.predecessors.map { frames.getValue(it) }.firstOrNull()
            predFrame?.stack?.forEach { stack.push(it) }
            predFrame?.locals?.forEach { local, value -> locals[local] = value }
        }
    }

    private fun isDebugNode(node: AbstractInsnNode) = when (node) {
        is LineNumberNode -> true
        is FrameNode -> true
        else -> false
    }

    private fun isTerminateInst(insn: AbstractInsnNode) = when {
        isDebugNode(insn) -> false
        else -> isTerminateInst(insn.opcode)
    }

    private fun throwsException(insn: AbstractInsnNode) = when {
        isDebugNode(insn) -> false
        else -> isExceptionThrowing(insn.opcode)
    }

    private fun convertConst(insn: InsnNode) {
        val opcode = insn.opcode
        val cnst = when (opcode) {
            ACONST_NULL -> VF.getNullConstant()
            ICONST_M1 -> VF.getIntConstant(-1)
            in ICONST_0..ICONST_5 -> VF.getIntConstant(opcode - ICONST_0)
            in LCONST_0..LCONST_1 -> VF.getLongConstant((opcode - LCONST_0).toLong())
            in FCONST_0..FCONST_2 -> VF.getFloatConstant((opcode - FCONST_0).toFloat())
            in DCONST_0..DCONST_1 -> VF.getDoubleConstant((opcode - DCONST_0).toDouble())
            else -> throw InvalidOpcodeError("Unknown const $opcode")
        }
        stack.push(cnst)
    }

    private fun convertArrayLoad(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val index = stack.pop()
        val arrayRef = stack.pop()
        if (arrayRef.type === NullType) {
            println(method.print())
            println(method.mn.print())
        }
        val inst = IF.getArrayLoad(arrayRef, index)
        addInstruction(bb, inst)
        stack.push(inst)
    }

    private fun convertArrayStore(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val value = stack.pop()
        val index = stack.pop()
        val array = stack.pop()
        addInstruction(bb, IF.getArrayStore(array, index, value))
    }

    private fun convertPop(insn: InsnNode) {
        val opcode = insn.opcode
        when (opcode) {
            POP -> stack.pop()
            POP2 -> {
                val top = stack.pop()
                if (!top.type.isDWord()) stack.pop()
            }
            else -> throw InvalidOpcodeError("Pop opcode $opcode")
        }
    }

    private fun convertDup(insn: InsnNode) {
        val opcode = insn.opcode
        when (opcode) {
            DUP -> stack.push(stack.peek())
            DUP_X1 -> {
                val top = stack.pop()
                val prev = stack.pop()
                stack.push(top)
                stack.push(prev)
                stack.push(top)
            }
            DUP_X2 -> {
                val val1 = stack.pop()
                val val2 = stack.pop()
                if (val2.type.isDWord()) {
                    stack.push(val1)
                    stack.push(val2)
                    stack.push(val1)
                } else {
                    val val3 = stack.pop()
                    stack.push(val1)
                    stack.push(val3)
                    stack.push(val2)
                    stack.push(val1)
                }
            }
            DUP2 -> {
                val top = stack.pop()
                if (top.type.isDWord()) {
                    stack.push(top)
                    stack.push(top)
                } else {
                    val bot = stack.pop()
                    stack.push(bot)
                    stack.push(top)
                    stack.push(bot)
                    stack.push(top)
                }
            }
            DUP2_X1 -> {
                val val1 = stack.pop()
                if (val1.type.isDWord()) {
                    val val2 = stack.pop()
                    stack.push(val1)
                    stack.push(val2)
                    stack.push(val1)
                } else {
                    val val2 = stack.pop()
                    val val3 = stack.pop()
                    stack.push(val2)
                    stack.push(val1)
                    stack.push(val3)
                    stack.push(val2)
                    stack.push(val1)
                }
            }
            DUP2_X2 -> {
                val val1 = stack.pop()
                if (val1.type.isDWord()) {
                    val val2 = stack.pop()
                    if (val2.type.isDWord()) {
                        stack.push(val1)
                        stack.push(val2)
                        stack.push(val1)
                    } else {
                        val val3 = stack.pop()
                        stack.push(val1)
                        stack.push(val3)
                        stack.push(val2)
                        stack.push(val1)
                    }
                } else {
                    val val2 = stack.pop()
                    val val3 = stack.pop()
                    if (val3.type.isDWord()) {
                        stack.push(val2)
                        stack.push(val1)
                        stack.push(val3)
                        stack.push(val2)
                        stack.push(val1)
                    } else {
                        val val4 = stack.pop()
                        stack.push(val2)
                        stack.push(val1)
                        stack.push(val4)
                        stack.push(val3)
                        stack.push(val2)
                        stack.push(val1)
                    }
                }
            }
            else -> throw InvalidOpcodeError("Dup opcode $opcode")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun convertSwap(insn: InsnNode) {
        val top = stack.pop()
        val bot = stack.pop()
        stack.push(top)
        stack.push(bot)
    }

    private fun convertBinary(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val rhv = stack.pop()
        val lhv = stack.pop()
        val binOp = toBinaryOpcode(insn.opcode)
        val inst = IF.getBinary(binOp, lhv, rhv)
        addInstruction(bb, inst)
        stack.push(inst)
    }

    private fun convertUnary(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val operand = stack.pop()
        val op = when (insn.opcode) {
            in INEG..DNEG -> UnaryOpcode.NEG
            ARRAYLENGTH -> UnaryOpcode.LENGTH
            else -> throw InvalidOpcodeError("Unary opcode ${insn.opcode}")
        }
        val inst = IF.getUnary(op, operand)
        addInstruction(bb, inst)
        stack.push(inst)
    }

    private fun convertCast(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val op = stack.pop()
        val type = when (insn.opcode) {
            in arrayOf(I2L, F2L, D2L) -> TF.getLongType()
            in arrayOf(I2F, L2F, D2F) -> TF.getFloatType()
            in arrayOf(I2D, L2D, F2D) -> TF.getDoubleType()
            in arrayOf(L2I, F2I, D2I) -> TF.getIntType()
            I2B -> TF.getByteType()
            I2C -> TF.getCharType()
            I2S -> TF.getShortType()
            else -> throw InvalidOpcodeError("Cast opcode ${insn.opcode}")
        }
        val inst = IF.getCast(type, op)
        addInstruction(bb, inst)
        stack.push(inst)
    }

    private fun convertCmp(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val lhv = stack.pop()
        val rhv = stack.pop()
        val op = toCmpOpcode(insn.opcode)
        val resType = getCmpResultType(op)
        val inst = IF.getCmp(resType, op, lhv, rhv)
        addInstruction(bb, inst)
        stack.push(inst)
    }

    private fun convertReturn(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        when (RETURN) {
            insn.opcode -> addInstruction(bb, IF.getReturn())
            else -> {
                val retval = stack.pop()
                addInstruction(bb, IF.getReturn(retval))
            }
        }
    }

    private fun convertMonitor(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val owner = stack.pop()
        when (insn.opcode) {
            MONITORENTER -> addInstruction(bb, IF.getEnterMonitor(owner))
            MONITOREXIT -> addInstruction(bb, IF.getExitMonitor(owner))
            else -> throw InvalidOpcodeError("Monitor opcode ${insn.opcode}")
        }
    }

    private fun convertThrow(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val throwable = stack.pop()
        addInstruction(bb, IF.getThrow(throwable))
    }

    private fun convertLocalLoad(insn: VarInsnNode) {
        stack.push(locals[insn.`var`])
    }

    private fun convertLocalStore(insn: VarInsnNode) {
        locals[insn.`var`] = stack.pop()
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
            BIPUSH -> stack.push(VF.getIntConstant(operand))
            SIPUSH -> stack.push(VF.getIntConstant(operand))
            NEWARRAY -> {
                val type = parsePrimaryType(operand)
                val count = stack.pop()
                val inst = IF.getNewArray(type, count)
                addInstruction(bb, inst)
                stack.push(inst)
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
            parseDesc(insn.desc)
        } catch (e: InvalidTypeDescError) {
            TF.getRefType(insn.desc)
        }
        when (opcode) {
            NEW -> {
                val inst = IF.getNew(type)
                addInstruction(bb, inst)
                stack.push(inst)
            }
            ANEWARRAY -> {
                val count = stack.pop()
                val inst = IF.getNewArray(type, count)
                addInstruction(bb, inst)
                stack.push(inst)
            }
            CHECKCAST -> {
                val castable = stack.pop()
                val inst = IF.getCast(type, castable)
                addInstruction(bb, inst)
                stack.push(inst)
            }
            INSTANCEOF -> {
                val obj = stack.pop()
                val inst = IF.getInstanceOf(type, obj)
                addInstruction(bb, inst)
                stack.push(inst)
            }
            else -> InvalidOpcodeError("$opcode in TypeInsn")
        }
    }

    private fun convertFieldInsn(insn: FieldInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val opcode = insn.opcode
        val fieldType = parseDesc(insn.desc)
        val `class` = CM.getByName(insn.owner)
        when (opcode) {
            GETSTATIC -> {
                val field = `class`.getField(insn.name, fieldType)
                val inst = IF.getFieldLoad(field)
                addInstruction(bb, inst)
                stack.push(inst)
            }
            PUTSTATIC -> {
                val field = `class`.getField(insn.name, fieldType)
                val value = stack.pop()
                addInstruction(bb, IF.getFieldStore(field, value))
            }
            GETFIELD -> {
                val field = `class`.getField(insn.name, fieldType)
                val owner = stack.pop()
                val inst = IF.getFieldLoad(owner, field)
                addInstruction(bb, inst)
                stack.push(inst)
            }
            PUTFIELD -> {
                val value = stack.pop()
                val owner = stack.pop()
                val field = `class`.getField(insn.name, fieldType)
                addInstruction(bb, IF.getFieldStore(owner, field, value))
            }
        }
    }

    private fun convertMethodInsn(insn: MethodInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val `class` = CM.getByName(insn.owner)
        val method = `class`.getMethod(insn.name, insn.desc)
        val args = arrayListOf<Value>()
        method.desc.args.forEach { args.add(0, stack.pop()) }

        val isNamed = !method.desc.retval.isVoid()
        val opcode = toCallOpcode(insn.opcode)
        val call = when (insn.opcode) {
            INVOKESTATIC -> IF.getCall(opcode, method, `class`, args.toTypedArray(), isNamed)
            in arrayOf(INVOKEVIRTUAL, INVOKESPECIAL, INVOKEINTERFACE) -> {
                val obj = stack.pop()
                IF.getCall(opcode, method, `class`, obj, args.toTypedArray(), isNamed)
            }
            else -> throw InvalidOpcodeError("Method insn opcode ${insn.opcode}")
        }
        addInstruction(bb, call)
        if (isNamed) {
            stack.push(call)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun convertInvokeDynamicInsn(insn: InvokeDynamicInsnNode): Unit = throw UnsupportedOperation("InvokeDynamicInsn")

    private fun convertJumpInsn(insn: JumpInsnNode) {
        val bb = nodeToBlock.getValue(insn)

        when (GOTO) {
            insn.opcode -> {
                val trueSuccessor = nodeToBlock.getValue(insn.label)
                addInstruction(bb, IF.getJump(trueSuccessor))
            }
            else -> {
                val falseSuccessor = nodeToBlock.getValue(insn.next)
                val trueSuccessor = nodeToBlock.getValue(insn.label)
                val name = Slot()
                val rhv = stack.pop()
                val opc = toCmpOpcode(insn.opcode)
                val resType = getCmpResultType(opc)
                val cond = when (insn.opcode) {
                    in IFEQ..IFLE -> IF.getCmp(name, resType, opc, rhv, VF.getZeroConstant(rhv.type))
                    in IF_ICMPEQ..IF_ACMPNE -> IF.getCmp(name, resType, opc, stack.pop(), rhv)
                    in IFNULL..IFNONNULL -> IF.getCmp(name, resType, opc, rhv, VF.getNullConstant())
                    else -> throw InvalidOpcodeError("Jump opcode ${insn.opcode}")
                }
                addInstruction(bb, cond)
                val castedCond = if (cond.type is BoolType) cond else IF.getCast(TF.getBoolType(), cond)
                addInstruction(bb, IF.getBranch(castedCond, trueSuccessor, falseSuccessor))
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun convertLabel(lbl: LabelNode) = Unit

    private fun convertLdcInsn(insn: LdcInsnNode) {
        val cst = insn.cst
        when (cst) {
            is Int -> stack.push(VF.getIntConstant(cst))
            is Float -> stack.push(VF.getFloatConstant(cst))
            is Double -> stack.push(VF.getDoubleConstant(cst))
            is Long -> stack.push(VF.getLongConstant(cst))
            is String -> stack.push(VF.getStringConstant(cst))
            is org.objectweb.asm.Type -> stack.push(VF.getClassConstant(cst.descriptor))
            is org.objectweb.asm.Handle -> {
                val `class` = CM.getByName(cst.owner)
                val method = `class`.getMethod(cst.name, cst.desc)
                stack.push(VF.getMethodConstant(method))
            }
            else -> throw InvalidOperandError("Unknown object $cst")
        }
    }

    private fun convertIincInsn(insn: IincInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val lhv = locals[insn.`var`] ?: throw InvalidOperandError("${insn.`var`} local is invalid")
        val rhv = IF.getBinary(BinaryOpcode.Add(), VF.getIntConstant(insn.incr), lhv)
        locals[insn.`var`] = rhv
        addInstruction(bb, rhv)
    }

    private fun convertTableSwitchInsn(insn: TableSwitchInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val index = stack.pop()
        val min = VF.getIntConstant(insn.min)
        val max = VF.getIntConstant(insn.max)
        val default = nodeToBlock.getValue(insn.dflt)
        val branches = insn.labels.map { nodeToBlock.getValue(it as AbstractInsnNode) }.toTypedArray()
        addInstruction(bb, IF.getTableSwitch(index, min, max, default, branches))
    }

    private fun convertLookupSwitchInsn(insn: LookupSwitchInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val default = nodeToBlock.getValue(insn.dflt)
        val branches = hashMapOf<Value, BasicBlock>()
        val key = stack.pop()
        for (i in 0..insn.keys.lastIndex) {
            branches[VF.getIntConstant(insn.keys[i] as Int)] = nodeToBlock.getValue(insn.labels[i] as LabelNode)
        }
        addInstruction(bb, IF.getSwitch(key, default, branches))
    }

    private fun convertMultiANewArrayInsn(insn: MultiANewArrayInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        super.visitMultiANewArrayInsn(insn.desc, insn.dims)
        val dimensions = arrayListOf<Value>()
        for (it in 0 until insn.dims) dimensions.add(stack.pop())
        val type = parseDesc(insn.desc)
        val inst = IF.getNewArray(type, dimensions.toTypedArray())
        addInstruction(bb, inst)
        stack.push(inst)
    }

    private fun convertLineNumber(insn: LineNumberNode) {
        val `package` = method.`class`.`package`
        val file = method.`class`.cn.sourceFile
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
                insn.type != null -> TF.getRefType(insn.type)
                else -> CatchBlock.defaultException
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
                        // add entry block if first insn of method is label
                        bb = nodeToBlock.getOrPut(insn) { bb }

                        val entry = BodyBlock("entry")
                        addInstruction(entry, IF.getJump(bb))
                        entry.addSuccessor(bb)
                        blockToNode[entry] = arrayListOf()
                        bb.addPredecessor(entry)

                        method.add(entry)
                    }
                    else -> {
                        bb = nodeToBlock.getOrPut(insn) { BodyBlock("label") }
                        insnList = blockToNode.getOrPut(bb, ::arrayListOf)

                        if (!isTerminateInst(insn.previous)) {
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
                            val lblBB = nodeToBlock.getOrPut(lbl, { BodyBlock("switch") })
                            bb.addSuccessors(lblBB)
                            lblBB.addPredecessor(bb)
                        }
                    }
                    else -> {
                        if (throwsException(insn) && (insn.next != null)) {
                            val next = nodeToBlock.getOrPut(insn.next) { BodyBlock("bb") }
                            if (!isTerminateInst(insn)) {
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
            val preds = bb.getPredSet()
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
                    val newPhi = IF.getPhi(phi.name, phi.type, incomings)
                    phi.replaceAllUsesWith(newPhi)
                    bb.replace(phi, newPhi)
                }
                else -> {
                    phi.replaceAllUsesWith(incomingValues.first())
                    bb.remove(phi)
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
                    val type = mergeTypes(incomingValues.map { it.type }.toSet())
                    when (type) {
                        null -> removablePhis.add(phi)
                        else -> {
                            val newPhi = IF.getPhi(phi.name, type, incomings)
                            phi.replaceAllUsesWith(newPhi)
                            phi.operands.forEach { it.removeUser(phi) }
                            bb.replace(phi, newPhi)
                        }
                    }
                }
                else -> {
                    phi.replaceAllUsesWith(incomingValues.first())
                    phi.operands.forEach { it.removeUser(phi) }
                    bb.remove(phi)
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
        for (inst in method.flatten()) {
            if (inst is PhiInst) {
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
                operands.mapNotNull { it as? PhiInst }
                        .mapNotNull { if (it == top) null else it }
                        .forEach { processPhis.add(it) }

            } else if (removablePhis.containsAll(instUsers)) {
                operands.forEach { it.removeUser(top) }
                top.parent?.remove(top) ?: continue
                operands.mapNotNull { it as? PhiInst }.forEach { processPhis.add(it) }
            }
        }

        removablePhis.forEach {
            val instUsers = it.users.mapNotNull { it as? Instruction }
            val methodInstUsers = instUsers.mapNotNull { if (it.parent != null) it else null }
            require(methodInstUsers.isEmpty()) { "Instruction ${it.print()} still have usages" }
            it.parent?.remove(it)
        }
    }

    fun build(): Method {
        var localIndx = 0
        if (!method.isStatic) {
            val `this` = VF.getThis(TF.getRefType(method.`class`))
            locals[localIndx++] = `this`
            method.slottracker.addValue(`this`)
        }
        for ((indx, type) in method.desc.args.withIndex()) {
            val arg = VF.getArgument(indx, method, type)
            locals[localIndx] = arg
            if (type.isDWord()) localIndx += 2
            else ++localIndx
            method.slottracker.addValue(arg)
        }

        buildCFG()  // build basic blocks graph
        buildPhiBlocks() // find out to which bb we should insert phi insts using dominator tree
        buildFrames() // build frame maps for each basic block

        method.catchEntries.forEach { cb -> cb.getAllPredecessors().forEach { it.addSuccessor(cb) } }
        val (order, c) = TopologicalSorter(method.basicBlocks.toSet()).sort(method.entry)
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
                addInstruction(bb, IF.getJump(bb.successors.first()))
            }

            reserveState(bb)
        }

        buildPhiInstructions()
        RetvalBuilder(method).visit()

        method.slottracker.rerun()
        return method
    }
}