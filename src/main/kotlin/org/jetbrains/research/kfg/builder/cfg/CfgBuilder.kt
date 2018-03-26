package org.jetbrains.research.kfg.builder.cfg

import org.jetbrains.research.kfg.*
import org.jetbrains.research.kfg.ir.*
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.type.*
import org.jetbrains.research.kfg.util.*
import org.objectweb.asm.commons.JSRInlinerAdapter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import java.util.*

class LocalArray(private val locals: MutableMap<Int, Value> = mutableMapOf())
    : User<Value>, MutableMap<Int, Value> by locals {
    override fun clear() {
        values.forEach { it.removeUser(this) }
        locals.clear()
    }

    override fun put(key: Int, value: Value): Value? {
        value.addUser(this)
        val prev = locals.put(key, value)
        if (prev != null) prev.removeUser(this)
        return prev
    }

    override fun putAll(from: Map<out Int, Value>) {
        from.forEach {
            put(it.key, it.value)
        }
    }

    override fun remove(key: Int): Value? {
        val res = locals.remove(key)
        if (res != null) res.removeUser(this)
        return res
    }

    override fun replaceUsesOf(from: Value, to: Value) {
        entries.forEach { (key, value) ->
            if (value == from) {
                value.removeUser(this)
                locals[key] = to
                to.addUser(this)
            }
        }
    }
}

class FrameStack(private val stack: MutableList<Value> = mutableListOf()) : User<Value>, MutableList<Value> by stack {
    override fun replaceUsesOf(from: Value, to: Value) {
        stack.replaceAll { if (it == from) to else it }
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
    val ST = method.slottracker

    inner class StackFrame(val bb: BasicBlock) {
        val locals = LocalArray()
        val stack = FrameStack()

        val stackPhis = mutableListOf<PhiInst>()
        val localPhis = mutableMapOf<Int, PhiInst>()
    }

    private val phiBlocks = mutableSetOf<BasicBlock>()
    private val cycleEntries = mutableSetOf<BasicBlock>()
    private val locals = LocalArray()
    private val nodeToBlock = mutableMapOf<AbstractInsnNode, BasicBlock>()
    private val blockToNode = mutableMapOf<BasicBlock, MutableList<AbstractInsnNode>>()
    private val frames = mutableMapOf<BasicBlock, StackFrame>()
    private val stack = Stack<Value>()

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
            if (incomingValues.size > 1) {
                val newPhi = IF.getPhi(ST.getNextSlot(), incomingValues.first().type, incomings)
                bb.addInstruction(newPhi)
                stack.push(newPhi)
            } else {
                stack.push(incomingValues.first())
            }
        }
    }

    private fun createStackCyclePhis(sf: StackFrame, predFrames: List<StackFrame>, stackSize: Int) {
        val bb = sf.bb
        val stacks = predFrames.map { it.stack }
        for (indx in 0 until stackSize) {
            val type = stacks.map { it[indx] }.first().type
            val phi = IF.getPhi(ST.getNextSlot(), type, mapOf())
            bb.addInstruction(phi)
            stack.push(phi)
            sf.stackPhis.add(phi as PhiInst)
        }
    }

    private fun createLocalPhis(bb: BasicBlock, predFrames: List<StackFrame>, definedLocals: Set<Int>) {
        for (local in definedLocals) {
            val incomings = predFrames.mapNotNull {
                val value = it.locals[local]
                if (value != null) it.bb to value else null
            }.toMap()

            if (incomings.size < predFrames.size) continue

            val incomingValues = incomings.values.toSet()
            if (incomingValues.size > 1) {
                val type = mergeTypes(incomingValues.map { it.type }.toSet())
                if (type != null) {
                    val newPhi = IF.getPhi(ST.getNextSlot(), type, incomings)
                    bb.addInstruction(newPhi)
                    locals[local] = newPhi
                }
            } else {
                locals[local] = incomingValues.first()
            }
        }
    }

    private fun createLocalCyclePhis(sf: StackFrame, predFrames: List<StackFrame>, definedLocals: Set<Int>) {
        val bb = sf.bb
        for (local in definedLocals) {
            val type = predFrames.mapNotNull { it.locals[local] }.first().type
            val phi = IF.getPhi(ST.getNextSlot(), type, mapOf())
            bb.addInstruction(phi)
            locals[local] = phi
            sf.localPhis[local] = phi as PhiInst
        }
    }

    private fun recoverState(bb: BasicBlock) {
        if (bb is CatchBlock) {
            val inst = IF.getCatch(ST.getNextSlot(), bb.exception)
            bb.addInstruction(inst)
            stack.push(inst)

            val predFrames = bb.getAllPredecessors().map { frames.getValue(it) }
            val definedLocals = predFrames.map { it.locals.keys }.flatten().toSet()
            if (bb in cycleEntries) {
                createLocalCyclePhis(frames.getValue(bb), predFrames, definedLocals)
            } else {
                createLocalPhis(bb, predFrames, definedLocals)
            }

        } else if (bb in phiBlocks) {
            val sf = frames.getValue(bb)
            val predFrames = bb.predecessors.map { frames.getValue(it) }
            val stacks = predFrames.map { it.stack }
            val stackSizes = stacks.map { it.size }.toSet()

            if (bb in cycleEntries) {
                assert(stackSizes.size <= 2, { "Stack sizes of ${bb.name} predecessors are different" })

                val stackSize = stackSizes.max()!!
                createStackCyclePhis(sf, predFrames, stackSize)

                val definedLocals = predFrames.map { it.locals.keys }.flatten().toSet()
                createLocalCyclePhis(sf, predFrames, definedLocals)

            } else {
                assert(stackSizes.size == 1, { "Stack sizes of ${bb.name} predecessors are different" })
                createStackPhis(bb, predFrames, stackSizes.first())

                val definedLocals = predFrames.map { it.locals.keys }.flatten().toSet()
                createLocalPhis(bb, predFrames, definedLocals)
            }
        } else {
            val predFrame = bb.predecessors.map { frames.getValue(it) }.firstOrNull() ?: return
            for (it in predFrame.stack) stack.push(it)
            for ((local, value) in predFrame.locals) locals[local] = value
        }
    }

    private fun isTerminateInst(insn: AbstractInsnNode) = isTerminateInst(insn.opcode)
    private fun throwsException(insn: AbstractInsnNode) = isExceptionThrowing(insn.opcode)

    private fun convertConst(insn: InsnNode) {
        val opcode = insn.opcode
        val cnst = when (opcode) {
            ACONST_NULL -> VF.getNullConstant()
            ICONST_M1 -> VF.getIntConstant(-1)
            in ICONST_0..ICONST_5 -> VF.getIntConstant(opcode - ICONST_0)
            in LCONST_0..LCONST_1 -> VF.getLongConstant((opcode - LCONST_0).toLong())
            in FCONST_0..FCONST_2 -> VF.getFloatConstant((opcode - FCONST_0).toFloat())
            in DCONST_0..DCONST_1 -> VF.getDoubleConstant((opcode - DCONST_0).toDouble())
            else -> throw UnexpectedOpcodeException("Unknown const $opcode")
        }
        stack.push(cnst)
    }

    private fun convertArrayLoad(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val index = stack.pop()
        val arrayRef = stack.pop()
        if (arrayRef.type is NullType) {
            println(method.print())
            println(method.mn.print())
        }
        val inst = IF.getArrayLoad(ST.getNextSlot(), arrayRef, index)
        bb.addInstruction(inst)
        stack.push(inst)
    }

    private fun convertArrayStore(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val value = stack.pop()
        val index = stack.pop()
        val array = stack.pop()
        bb.addInstruction(IF.getArrayStore(array, index, value))
    }

    private fun convertPop(insn: InsnNode) {
        val opcode = insn.opcode
        when (opcode) {
            POP -> stack.pop()
            POP2 -> {
                val top = stack.pop()
                if (!top.type.isDWord()) stack.pop()
            }
            else -> throw UnexpectedOpcodeException("Pop opcode $opcode")
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
            else -> throw UnexpectedOpcodeException("Dup opcode $opcode")
        }
    }

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
        val inst = IF.getBinary(ST.getNextSlot(), binOp, lhv, rhv)
        bb.addInstruction(inst)
        stack.push(inst)
    }

    private fun convertUnary(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val operand = stack.pop()
        val op = when (insn.opcode) {
            in INEG..DNEG -> UnaryOpcode.NEG
            ARRAYLENGTH -> UnaryOpcode.LENGTH
            else -> throw InvalidOperandException("Unary opcode ${insn.opcode}")
        }
        val inst = IF.getUnary(ST.getNextSlot(), op, operand)
        bb.addInstruction(inst)
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
            else -> throw UnexpectedOpcodeException("Cast opcode ${insn.opcode}")
        }
        val inst = IF.getCast(ST.getNextSlot(), type, op)
        bb.addInstruction(inst)
        stack.push(inst)
    }

    private fun convertCmp(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val lhv = stack.pop()
        val rhv = stack.pop()
        val op = toCmpOpcode(insn.opcode)
        val inst = IF.getCmp(ST.getNextSlot(), op, lhv, rhv)
        bb.addInstruction(inst)
        stack.push(inst)
    }

    private fun convertReturn(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        if (insn.opcode == RETURN) {
            bb.addInstruction(IF.getReturn())
        } else {
            val retval = stack.pop()
            bb.addInstruction(IF.getReturn(retval))
        }
    }

    private fun convertMonitor(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val owner = stack.pop()
        when (insn.opcode) {
            MONITORENTER -> bb.addInstruction(IF.getEnterMonitor(owner))
            MONITOREXIT -> bb.addInstruction(IF.getExitMonitor(owner))
            else -> throw UnexpectedOpcodeException("Monitor opcode ${insn.opcode}")
        }
    }

    private fun convertThrow(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val throwable = stack.pop()
        bb.addInstruction(IF.getThrow(throwable))
    }

    private fun convertLocalLoad(insn: VarInsnNode) {
        stack.push(locals[insn.`var`])
    }

    private fun convertLocalStore(insn: VarInsnNode) {
        locals[insn.`var`] = stack.pop()
    }

    private fun convertInsn(insn: InsnNode) {
        when (insn.opcode) {
            NOP -> {
            }
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
            else -> throw UnexpectedOpcodeException("Insn opcode ${insn.opcode}")
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
                val inst = IF.getNewArray(ST.getNextSlot(), type, count)
                bb.addInstruction(inst)
                stack.push(inst)
            }
            else -> throw UnexpectedOpcodeException("IntInsn opcode $opcode")
        }
    }

    private fun convertVarInsn(insn: VarInsnNode) {
        when (insn.opcode) {
            in ISTORE..ASTORE -> convertLocalStore(insn)
            in ILOAD..ALOAD -> convertLocalLoad(insn)
            RET -> TODO()
            else -> throw UnexpectedOpcodeException("VarInsn opcode ${insn.opcode}")
        }
    }

    private fun convertTypeInsn(insn: TypeInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val opcode = insn.opcode
        val type = try {
            parseDesc(insn.desc)
        } catch (e: InvalidTypeDescException) {
            TF.getRefType(insn.desc)
        }
        when (opcode) {
            NEW -> {
                val inst = IF.getNew(ST.getNextSlot(), type)
                bb.addInstruction(inst)
                stack.push(inst)
            }
            ANEWARRAY -> {
                val count = stack.pop()
                val inst = IF.getNewArray(ST.getNextSlot(), type, count)
                bb.addInstruction(inst)
                stack.push(inst)
            }
            CHECKCAST -> {
                val castable = stack.pop()
                val inst = IF.getCast(ST.getNextSlot(), type, castable)
                bb.addInstruction(inst)
                stack.push(inst)
            }
            INSTANCEOF -> {
                val obj = stack.pop()
                val inst = IF.getInstanceOf(ST.getNextSlot(), type, obj)
                bb.addInstruction(inst)
                stack.push(inst)
            }
            else -> UnexpectedOpcodeException("$opcode in TypeInsn")
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
                val inst = IF.getFieldLoad(ST.getNextSlot(), field)
                bb.addInstruction(inst)
                stack.push(inst)
            }
            PUTSTATIC -> {
                val field = `class`.getField(insn.name, fieldType)
                val value = stack.pop()
                bb.addInstruction(IF.getFieldStore(field, value))
            }
            GETFIELD -> {
                val field = `class`.getField(insn.name, fieldType)
                val owner = stack.pop()
                val inst = IF.getFieldLoad(ST.getNextSlot(), owner, field)
                bb.addInstruction(inst)
                stack.push(inst)
            }
            PUTFIELD -> {
                val value = stack.pop()
                val owner = stack.pop()
                val field = `class`.getField(insn.name, fieldType)
                bb.addInstruction(IF.getFieldStore(owner, field, value))
            }
        }
    }

    private fun convertMethodInsn(insn: MethodInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val `class` = CM.getByName(insn.owner)
        val method = `class`.getMethod(insn.name, insn.desc)
        val args = mutableListOf<Value>()
        method.argTypes.forEach { args.add(0, stack.pop()) }

        val returnType = method.retType
        val opcode = toCallOpcode(insn.opcode)
        val call =
                if (returnType.isVoid()) {
                    when (insn.opcode) {
                        INVOKESTATIC -> IF.getCall(opcode, method, `class`, args.toTypedArray())
                        in arrayOf(INVOKEVIRTUAL, INVOKESPECIAL, INVOKEINTERFACE) -> {
                            val obj = stack.pop()
                            IF.getCall(opcode, method, `class`, obj, args.toTypedArray())
                        }
                        else -> throw UnexpectedOpcodeException("Method insn opcode ${insn.opcode}")
                    }
                } else {
                    when (insn.opcode) {
                        INVOKESTATIC -> IF.getCall(opcode, ST.getNextSlot(), method, `class`, args.toTypedArray())
                        in arrayOf(INVOKEVIRTUAL, INVOKESPECIAL, INVOKEINTERFACE) -> {
                            val obj = stack.pop()
                            IF.getCall(opcode, ST.getNextSlot(), method, `class`, obj, args.toTypedArray())
                        }
                        else -> throw UnexpectedOpcodeException("Method insn opcode ${insn.opcode}")
                    }
                }
        bb.addInstruction(call)
        if (!returnType.isVoid()) {
            stack.push(call)
        }
    }

    private fun convertInvokeDynamicInsn(insn: InvokeDynamicInsnNode): Nothing = TODO()

    private fun convertJumpInsn(insn: JumpInsnNode) {
        val bb = nodeToBlock.getValue(insn)

        if (insn.opcode == GOTO) {
            val trueSuccessor = nodeToBlock.getValue(insn.label)
            bb.addInstruction(IF.getJump(trueSuccessor))
        } else {
            val falseSuccessor = nodeToBlock.getValue(insn.next)
            val trueSuccessor = nodeToBlock.getValue(insn.label)
            val name = ST.getNextSlot()
            val rhv = stack.pop()
            val opc = toCmpOpcode(insn.opcode)
            val cond = when (insn.opcode) {
                in IFEQ..IFLE -> IF.getCmp(name, opc, VF.getZeroConstant(rhv.type), rhv)
                in IF_ICMPEQ..IF_ACMPNE -> IF.getCmp(name, opc, stack.pop(), rhv)
                in IFNULL..IFNONNULL -> IF.getCmp(name, opc, rhv, VF.getNullConstant())
                else -> throw UnexpectedOpcodeException("Jump opcode ${insn.opcode}")
            }
            bb.addInstruction(cond)
            bb.addInstruction(IF.getBranch(cond, trueSuccessor, falseSuccessor))
        }
    }

    private fun convertLabel(lbl: LabelNode) {}

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
            else -> throw InvalidOperandException("Unknown object $cst")
        }
    }

    private fun convertIincInsn(insn: IincInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val lhv = locals[insn.`var`] ?: throw InvalidOperandException("${insn.`var`} local is invalid")
        val rhv = IF.getBinary(ST.getNextSlot(), BinaryOpcode.Add(), VF.getIntConstant(insn.incr), lhv)
        locals[insn.`var`] = rhv
        bb.addInstruction(rhv)
    }

    private fun convertTableSwitchInsn(insn: TableSwitchInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val index = stack.pop()
        val min = VF.getIntConstant(insn.min)
        val max = VF.getIntConstant(insn.max)
        val default = nodeToBlock.getValue(insn.dflt)
        val branches = insn.labels.map { nodeToBlock.getValue(it as AbstractInsnNode) }.toTypedArray()
        bb.addInstruction(IF.getTableSwitch(index, min, max, default, branches))
    }

    private fun convertLookupSwitchInsn(insn: LookupSwitchInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val default = nodeToBlock.getValue(insn.dflt)
        val branches = mutableMapOf<Value, BasicBlock>()
        val key = stack.pop()
        for (i in 0..(insn.keys.size - 1)) {
            branches[VF.getIntConstant(insn.keys[i] as Int)] = nodeToBlock.getValue(insn.labels[i] as LabelNode)
        }
        bb.addInstruction(IF.getSwitch(key, default, branches))
    }

    private fun convertMultiANewArrayInsn(insn: MultiANewArrayInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        super.visitMultiANewArrayInsn(insn.desc, insn.dims)
        val type = parseDesc(insn.desc)
        val inst = IF.getMultiNewArray(ST.getNextSlot(), type, insn.dims)
        bb.addInstruction(inst)
        stack.push(inst)
    }

    private fun buildCFG() {
        var bbc = 0
        for (insn in method.mn.tryCatchBlocks as MutableList<TryCatchBlockNode>) {
            val type = if (insn.type != null) TF.getRefType(insn.type) else CatchBlock.defaultException
            nodeToBlock[insn.handler] = CatchBlock("%bb${bbc++}", method, type)
        }
        val getNextBlock = { BodyBlock("%bb${bbc++}", method) }
        var bb: BasicBlock = getNextBlock()
        var insnList = blockToNode.getOrPut(bb, { mutableListOf() })
        for (insn in method.mn.instructions) {
            if (insn is LabelNode) {
                if (insn.previous == null) { // when first instruction of method is label
                    bb = nodeToBlock.getOrPut(insn, { bb })
                    val entry = BodyBlock("entry", method)
                    entry.addInstruction(IF.getJump(bb))
                    entry.addSuccessor(bb)
                    blockToNode[entry] = mutableListOf()
                    bb.addPredecessor(entry)

                    method.addIfNotContains(entry)
                } else {
                    bb = nodeToBlock.getOrPut(insn, getNextBlock)
                    insnList = blockToNode.getOrPut(bb, { mutableListOf() })
                    if (!isTerminateInst(insn.previous)) {
                        val prev = nodeToBlock[insn.previous]
                        bb.addPredecessor(prev!!)
                        prev.addSuccessor(bb)
                    }
                }
            } else {
                bb = nodeToBlock.getOrPut(insn as AbstractInsnNode, { bb })
                insnList = blockToNode.getOrPut(bb, { mutableListOf() })
                if (insn is JumpInsnNode) {
                    if (insn.opcode != GOTO) {
                        val falseSuccessor = nodeToBlock.getOrPut(insn.next, getNextBlock)
                        bb.addSuccessor(falseSuccessor)
                        falseSuccessor.addPredecessor(bb)
                    }
                    val trueSuccessor = nodeToBlock.getOrPut(insn.label, getNextBlock)
                    bb.addSuccessor(trueSuccessor)
                    trueSuccessor.addPredecessor(bb)
                } else if (insn is TableSwitchInsnNode) {
                    val default = nodeToBlock.getOrPut(insn.dflt, getNextBlock)
                    bb.addSuccessors(default)
                    default.addPredecessor(bb)
                    for (lbl in insn.labels as MutableList<LabelNode>) {
                        val lblBB = nodeToBlock.getOrPut(lbl, getNextBlock)
                        bb.addSuccessors(lblBB)
                        lblBB.addPredecessor(bb)
                    }
                } else if (insn is LookupSwitchInsnNode) {
                    val default = nodeToBlock.getOrPut(insn.dflt, getNextBlock)
                    bb.addSuccessors(default)
                    default.addPredecessor(bb)
                    for (lbl in insn.labels as MutableList<LabelNode>) {
                        val lblBB = nodeToBlock.getOrPut(lbl, getNextBlock)
                        bb.addSuccessors(lblBB)
                        lblBB.addPredecessor(bb)
                    }
                } else if (throwsException(insn) && insn.next != null) {
                    val next = nodeToBlock.getOrPut(insn.next, getNextBlock)
                    if (!isTerminateInst(insn)) {
                        bb.addSuccessor(next)
                        next.addPredecessor(bb)
                    }
                }
            }
            insnList.add(insn as AbstractInsnNode)
            method.addIfNotContains(bb)
        }
        for (insn in method.mn.tryCatchBlocks as MutableList<TryCatchBlockNode>) {
            val handle = nodeToBlock.getValue(insn.handler) as CatchBlock
            nodeToBlock[insn.handler] = handle
            var cur = insn.start as AbstractInsnNode
            var thrower = nodeToBlock.getValue(cur)
            val throwers = mutableListOf<BasicBlock>()
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
        val sf = frames.getOrPut(method.getEntry(), { StackFrame(method.getEntry()) })
        sf.locals.putAll(locals)

        for (bb in method.basicBlocks.drop(1)) {
            frames.getOrPut(bb, { StackFrame(bb) })
        }
    }

    private fun buildPhiBlocks() {
        val nodes = method.basicBlocks.map { it as GraphNode }.toSet()
        val dominatorTree = DominatorTreeBuilder(nodes).build()

        for (it in dominatorTree) {
            val preds = it.key.getPredSet()
            if (preds.size > 1) {
                for (pred in preds) {
                    var runner: GraphNode? = pred
                    while (runner != null && runner != it.value.idom?.value) {
                        phiBlocks.add(it.key as BasicBlock)
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
            if (incomingValues.size > 1) {
                val newPhi = IF.getPhi(phi.name, phi.type, incomings)
                phi.replaceAllUsesWith(newPhi)
                bb.replace(phi, newPhi)
            } else {
                phi.replaceAllUsesWith(incomingValues.first())
                bb.remove(phi)
            }
            phi.operands.forEach { it.removeUser(phi) }
        }
    }

    private fun recoverLocalPhis(sf: StackFrame, predFrames: List<StackFrame>): Set<PhiInst> {
        val removablePhis = mutableSetOf<PhiInst>()
        val bb = sf.bb
        for ((local, phi) in sf.localPhis) {
            val incomings = predFrames.mapNotNull {
                val value = it.locals[local]
                if (value != null) it.bb to value else null
            }.toMap()

            if (incomings.size < predFrames.size) {
                removablePhis.add(phi)
                continue
            }

            val incomingValues = incomings.values.toSet()
            if (incomingValues.size > 1) {
                val type = mergeTypes(incomingValues.map { it.type }.toSet())
                if (type == null) {
                    removablePhis.add(phi)
                } else {
                    val newPhi = IF.getPhi(phi.name, type, incomings)
                    phi.replaceAllUsesWith(newPhi)
                    phi.operands.forEach { it.removeUser(phi) }
                    bb.replace(phi, newPhi)
                }
            } else {
                phi.replaceAllUsesWith(incomingValues.first())
                phi.operands.forEach { it.removeUser(phi) }
                bb.remove(phi)
            }
        }
        return removablePhis
    }

    private fun buildPhiInstructions() {
        val removablePhis = mutableSetOf<PhiInst>()
        val processPhis = mutableListOf<PhiInst>()
        if (method.name == "test1")
            println()
        for ((bb, sf) in frames) {
            if (bb !in phiBlocks && bb !in cycleEntries) continue

            val predFrames = ((bb as? CatchBlock)?.getAllPredecessors() ?: bb.predecessors).map { frames.getValue(it) }
            val stacks = predFrames.map { it.stack }
            val stackSizes = stacks.map { it.size }.toSet()
            assert(stackSizes.size == 1, { "Stack sizes of ${bb.name} predecessors are different" })

            recoverStackPhis(sf, predFrames)

            val removable = recoverLocalPhis(sf, predFrames)
            removablePhis.addAll(removable)
        }
        for (it in method.flatten()) {
            if (it is PhiInst) {
                val incomings = it.getIncomingValues()
                val instUsers = it.getUsers().mapNotNull { it as? Instruction }
                if (instUsers.isEmpty()) removablePhis.add(it)
                else if (instUsers.size == 1 && instUsers.first() == it) removablePhis.add(it)
                else if (incomings.size == 2 && incomings.contains(it)) {
                    if (incomings.first() == it) it.replaceAllUsesWith(incomings.last())
                    else it.replaceAllUsesWith(incomings.first())
                    it.operands.forEach { op -> op.removeUser(it) }
                    instUsers.mapNotNull { it as? PhiInst }.forEach { processPhis.add(it) }
                }
            }
        }

        processPhis.addAll(removablePhis)
        while (processPhis.isNotEmpty()) {
            val top = processPhis.first()
            processPhis.removeAt(0)
            val incomings = top.getIncomingValues()
            val incomingsSet = incomings.toSet()
            val instUsers = top.getUsers().mapNotNull { it as? Instruction }
            if (incomingsSet.size == 1) {
                val first = incomingsSet.first()
                top.replaceAllUsesWith(first)
                top.operands.forEach { it.removeUser(top) }
                if (first is PhiInst) processPhis.add(first)
                top.parent?.remove(top) ?: continue
                top.operands.mapNotNull { it as? PhiInst }.forEach { processPhis.add(it) }
            } else if (incomings.size == 2 && incomings.contains(top)) {
                if (incomings.first() == top) top.replaceAllUsesWith(incomings.last())
                else top.replaceAllUsesWith(incomings.first())
                top.operands.forEach { it.removeUser(top) }
                instUsers.mapNotNull { it as? PhiInst }.forEach { processPhis.add(it) }
            } else if (instUsers.isEmpty()) {
                top.operands.forEach { it.removeUser(top) }
                top.parent?.remove(top) ?: continue
                top.operands.mapNotNull { it as? PhiInst }.forEach { processPhis.add(it) }
            } else if (instUsers.size == 1 && instUsers.first() == top) {
                top.operands.forEach { it.removeUser(top) }
                top.parent?.remove(top)
                top.operands
                        .mapNotNull { it as? PhiInst }
                        .mapNotNull { if (it == top) null else it }
                        .forEach { processPhis.add(it) }
            } else if (removablePhis.containsAll(instUsers)) {
                top.operands.forEach { it.removeUser(top) }
                top.parent?.remove(top) ?: continue
                top.operands.mapNotNull { it as? PhiInst }.forEach { processPhis.add(it) }
            }
        }
        removablePhis.forEach {
            val instUsers = it.getUsers().mapNotNull { it as? Instruction }
            val methodInstUsers = instUsers.mapNotNull { if (it.parent != null) it else null }
            assert(methodInstUsers.isEmpty(), { "Instruction ${it.print()} still have usages" })
            if (it.parent != null) it.parent!!.remove(it)
        }
    }

    fun build(): Method {
        var localIndx = 0
        if (!method.isStatic()) locals[localIndx++] = VF.getThis(TF.getRefType(method.`class`))
        for ((indx, type) in method.argTypes.withIndex()) {
            locals[localIndx] = VF.getArgument("arg$$indx", method, type)
            if (type.isDWord()) localIndx += 2
            else ++localIndx
        }

        // if the first instruction of method is label, add special BB before it, so method entry have no predecessors
        buildCFG()  // build basic blocks graph
        buildPhiBlocks() // find out to which bb we should insert phi insts using dominator tree
        buildFrames() // build frame maps for each basic block

        val nodes = method.basicBlocks.map { it as GraphNode }.toSet()
        val order = mutableListOf<BasicBlock>()

        val catches = method.catchBlocks.map { it as CatchBlock }
        catches.forEach { cb -> cb.getAllPredecessors().forEach { it.addSuccessor(cb) } }
        val (o, c) = TopologicalSorter(nodes).sort(method.getEntry())
        order.addAll(o.reversed().map { it as BasicBlock })
        cycleEntries.addAll(c.map { it as BasicBlock })
        catches.forEach { cb -> cb.getAllPredecessors().forEach { it.successors.remove(cb) } }


        for (bb in order) {
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
                    else -> throw UnexpectedOpcodeException("Unknown insn: ${insn.print()}")
                }
            }
            reserveState(bb)

            val last = bb.instructions.lastOrNull()
            if (last == null || !last.isTerminate()) {
                assert(bb.successors.size == 1)
                bb.addInstruction(IF.getJump(bb.successors.first()))
            }
        }

        buildPhiInstructions()

        method.slottracker.rerun()
        return method
    }
}