package org.jetbrains.research.kfg.builder.cfg

import org.jetbrains.research.kfg.*
import org.jetbrains.research.kfg.ir.*
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.type.*
import org.objectweb.asm.commons.JSRInlinerAdapter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import java.util.*

class LocalArray : User<Value>, MutableMap<Int, Value> {
    private val locals = mutableMapOf<Int, Value>()

    override val entries: MutableSet<MutableMap.MutableEntry<Int, Value>>
        get() = locals.entries
    override val keys: MutableSet<Int>
        get() = locals.keys
    override val size: Int
        get() = locals.size
    override val values: MutableCollection<Value>
        get() = locals.values

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

    override fun containsKey(key: Int) = locals.containsKey(key)
    override fun containsValue(value: Value) = locals.containsValue(value)
    override fun get(key: Int) = locals.get(key)
    override fun isEmpty() = locals.isEmpty()

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

class MethodBuilder(val method: Method, val mn: MethodNode)
    : JSRInlinerAdapter(Opcodes.ASM5, mn, mn.access, mn.name, mn.desc, mn.signature, mn.exceptions.map { it as String }.toTypedArray()) {
    val ST = method.slottracker

    inner class StackFrame(val bb: BasicBlock) {
        val locals = LocalArray()
        val stack = mutableListOf<Value>()
        val modifiedLocals = mutableSetOf<Int>()
        val readedLocals = mutableSetOf<Int>()

        val stackPhis = mutableListOf<PhiInst>()
        val localPhis = mutableMapOf<Int, PhiInst>()
    }

    private val locals = LocalArray()
    private val nodeToBlock = mutableMapOf<AbstractInsnNode, BasicBlock>()
    private val frames = mutableMapOf<BasicBlock, StackFrame>()
    private val stack = Stack<Value>()

    private fun getFrame(bb: BasicBlock) = frames[bb]
            ?: throw UnexpectedException("No frame for basic block ${bb.name}")

    private fun reserveState(bb: BasicBlock) {
        val sf = getFrame(bb)
        sf.stack.addAll(stack)
        sf.locals.putAll(locals)
    }

    private fun recoverState(bb: BasicBlock) {
        if (bb.predecessors.isEmpty()) return
        val sf = getFrame(bb)
        val predFrames = bb.predecessors.map { getFrame(it) }
        stack.clear()
        val stacks = predFrames.map { it.stack }
        val stackSizes = stacks.map { it.size }.toSet()
        require(stackSizes.size <= 2, { "Stack sizes of ${bb.name} predecessors are different" })
        val stackSize = stackSizes.max()!!
        for (indx in 0 until stackSize) {
            val type = stacks.map { it[indx] }.first().type
            val phi = IF.getPhi(ST.getNextSlot(), type, mapOf())
            bb.addInstruction(phi)
            stack.push(phi)
            sf.stackPhis.add(phi as PhiInst)
        }
        for (local in sf.readedLocals) {
            val type = predFrames.mapNotNull { it.locals[local] }.first().type
            val phi = IF.getPhi(ST.getNextSlot(), type, mapOf())
            bb.addInstruction(phi)
            locals[local] = phi
            sf.localPhis[local] = phi as PhiInst
        }
    }

    private fun isTerminateInst(insn: AbstractInsnNode): Boolean {
        if (insn is JumpInsnNode && insn.opcode == GOTO) return true
        if (insn is InsnNode && insn.opcode in IRETURN..RETURN) return true
        if (insn is InsnNode && insn.opcode == ATHROW) return true
        return false
    }

    private fun getBasicBlock(insn: AbstractInsnNode) = nodeToBlock[insn]
            ?: throw UnexpectedException("Unknown node $insn")

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
        val bb = getBasicBlock(insn)
        val index = stack.pop()
        val arrayRef = stack.pop()
        val inst = IF.getArrayLoad(ST.getNextSlot(), arrayRef, index)
        bb.addInstruction(inst)
        stack.push(inst)
    }

    private fun convertArrayStore(insn: InsnNode) {
        val bb = getBasicBlock(insn)
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
                    val val3 = stack.pop()
                    stack.push(val1)
                    stack.push(val3)
                    stack.push(val2)
                    stack.push(val1)
                } else {
                    val val2 = stack.pop()
                    val val3 = stack.pop()
                    val val4 = stack.pop()
                    stack.push(val2)
                    stack.push(val1)
                    stack.push(val4)
                    stack.push(val3)
                    stack.push(val2)
                    stack.push(val1)
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
        val bb = getBasicBlock(insn)
        val lhv = stack.pop()
        val rhv = stack.pop()
        val binOp = toBinaryOpcode(insn.opcode)
        val inst = IF.getBinary(ST.getNextSlot(), binOp, lhv, rhv)
        bb.addInstruction(inst)
        stack.push(inst)
    }

    private fun convertUnary(insn: InsnNode) {
        val bb = getBasicBlock(insn)
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
        val bb = getBasicBlock(insn)
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
        val bb = getBasicBlock(insn)
        val lhv = stack.pop()
        val rhv = stack.pop()
        val op = toCmpOpcode(insn.opcode)
        val inst = IF.getCmp(ST.getNextSlot(), op, lhv, rhv)
        bb.addInstruction(inst)
        stack.push(inst)
    }

    private fun convertReturn(insn: InsnNode) {
        val bb = getBasicBlock(insn)
        if (insn.opcode == RETURN) {
            bb.addInstruction(IF.getReturn())
        } else {
            val retval = stack.pop()
            bb.addInstruction(IF.getReturn(retval))
        }
    }

    private fun convertMonitor(insn: InsnNode) {
        val bb = getBasicBlock(insn)
        val owner = stack.pop()
        when (insn.opcode) {
            MONITORENTER -> bb.addInstruction(IF.getEnterMonitor(owner))
            MONITOREXIT -> bb.addInstruction(IF.getExitMonitor(owner))
            else -> throw UnexpectedOpcodeException("Monitor opcode ${insn.opcode}")
        }
    }

    private fun convertThrow(insn: InsnNode) {
        val bb = getBasicBlock(insn)
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
        val bb = getBasicBlock(insn)
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
        val bb = getBasicBlock(insn)
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
        val bb = getBasicBlock(insn)
        val opcode = insn.opcode
        val fieldType = parseDesc(insn.desc)
        val `class` = CM.getByName(insn.owner)
        when (opcode) {
            GETSTATIC -> {
                val inst = IF.getFieldLoad(ST.getNextSlot(), VF.getField(insn.name, `class`, fieldType))
                bb.addInstruction(inst)
                stack.push(inst)
            }
            PUTSTATIC -> {
                val field = VF.getField(insn.name, `class`, fieldType)
                val value = stack.pop()
                bb.addInstruction(IF.getFieldStore(field, value))
            }
            GETFIELD -> {
                val obj = stack.pop()
                val inst = IF.getFieldLoad(ST.getNextSlot(), VF.getField(insn.name, `class`, fieldType, obj))
                bb.addInstruction(inst)
                stack.push(inst)
            }
            PUTFIELD -> {
                val value = stack.pop()
                val obj = stack.pop()
                val field = VF.getField(insn.name, `class`, fieldType, obj)
                bb.addInstruction(IF.getFieldStore(field, value))
            }
        }
    }

    private fun convertMethodInsn(insn: MethodInsnNode) {
        val bb = getBasicBlock(insn)
        val `class` = CM.getByName(insn.owner)
        val method = `class`.getMethod(insn.name, insn.desc)
        val args = mutableListOf<Value>()
        method.argTypes.forEach {
            args.add(stack.pop())
        }
        val returnType = method.retType
        val call =
                if (returnType.isVoid()) {
                    when (insn.opcode) {
                        INVOKESTATIC -> IF.getCall(method, `class`, args.toTypedArray())
                        in arrayOf(INVOKEVIRTUAL, INVOKESPECIAL, INVOKEINTERFACE) -> {
                            val obj = stack.pop()
                            IF.getCall(method, `class`, obj, args.toTypedArray())
                        }
                        else -> throw UnexpectedOpcodeException("Method insn opcode ${insn.opcode}")
                    }
                } else {
                    when (insn.opcode) {
                        INVOKESTATIC -> IF.getCall(ST.getNextSlot(), method, `class`, args.toTypedArray())
                        in arrayOf(INVOKEVIRTUAL, INVOKESPECIAL, INVOKEINTERFACE) -> {
                            val obj = stack.pop()
                            IF.getCall(ST.getNextSlot(), method, `class`, obj, args.toTypedArray())
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
        val bb = getBasicBlock(insn)
        val falseSuccessor = getBasicBlock(insn.next)
        val trueSuccessor = getBasicBlock(insn.label)

        if (insn.opcode == GOTO) {
            reserveState(bb)
            bb.addInstruction(IF.getJump(trueSuccessor))
        } else {
            val name = ST.getNextSlot()
            val lhv = stack.pop()
            val opc = toCmpOpcode(insn.opcode)
            val cond = when (insn.opcode) {
                in IFEQ..IFLE -> IF.getCmp(name, opc, lhv, VF.getZeroConstant(lhv.type))
                in IF_ICMPEQ..IF_ACMPNE -> IF.getCmp(name, opc, lhv, stack.pop())
                in IFNULL..IFNONNULL -> IF.getCmp(name, opc, lhv, VF.getNullConstant())
                else -> throw UnexpectedOpcodeException("Jump opcode ${insn.opcode}")
            }
            reserveState(bb)
            bb.addInstruction(cond)
            bb.addInstruction(IF.getBranch(cond, trueSuccessor, falseSuccessor))
        }
    }

    private fun convertLabel(lbl: LabelNode) {
        val bb = getBasicBlock(lbl)
        // if we came to this label not by jump, but just by consistently executing instructions
        if (lbl.previous != null && !isTerminateInst(lbl.previous)) {
            val prev = getBasicBlock(lbl.previous)
            reserveState(prev)
            prev.addInstruction(IF.getJump(bb))
            bb.addPredecessor(prev)
            prev.addSuccessor(bb)
        }
        recoverState(bb)
        if (bb is CatchBlock) {
            val inst = IF.getCatch(ST.getNextSlot(), bb.exception)
            bb.addInstruction(inst)
            stack.push(inst)
        }
    }

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
        val bb = getBasicBlock(insn)
        val lhv = locals[insn.`var`] ?: throw InvalidOperandException("${insn.`var`} local is invalid")
        val rhv = IF.getBinary(ST.getNextSlot(), BinaryOpcode.ADD, lhv, VF.getIntConstant(insn.incr))
        locals[insn.`var`] = rhv
        bb.addInstruction(rhv)
    }

    private fun convertTableSwitchInsn(insn: TableSwitchInsnNode) {
        val bb = getBasicBlock(insn)
        val index = stack.pop()
        val min = VF.getIntConstant(insn.min)
        val max = VF.getIntConstant(insn.max)
        val default = getBasicBlock(insn.dflt)
        val branches = insn.labels.map { getBasicBlock(it as AbstractInsnNode) }.toTypedArray()
        bb.addInstruction(IF.getTableSwitch(index, min, max, default, branches))
    }

    private fun convertLookupSwitchInsn(insn: LookupSwitchInsnNode) {
        val bb = getBasicBlock(insn)
        val default = getBasicBlock(insn.dflt)
        val branches = mutableMapOf<Value, BasicBlock>()
        val key = stack.pop()
        for (i in 0..(insn.keys.size - 1)) {
            branches[VF.getIntConstant(insn.keys[i] as Int)] = getBasicBlock(insn.labels[i] as LabelNode)
        }
        bb.addInstruction(IF.getSwitch(key, default, branches))
    }

    private fun convertMultiANewArrayInsn(insn: MultiANewArrayInsnNode) {
        val bb = getBasicBlock(insn)
        super.visitMultiANewArrayInsn(insn.desc, insn.dims)
        val type = parseDesc(insn.desc) as ArrayType
        val inst = IF.getMultiNewArray(ST.getNextSlot(), type, insn.dims)
        bb.addInstruction(inst)
        stack.push(inst)
    }

    private fun buildCFG() {
        var bbc = 0
        for (insn in mn.tryCatchBlocks as MutableList<TryCatchBlockNode>) {
            val type = if (insn.type != null) TF.getRefType(insn.type) else CatchBlock.defaultException
            nodeToBlock[insn.handler] = CatchBlock("%bb${bbc++}", method, type)
        }
        var bb: BasicBlock = BodyBlock("%bb${bbc++}", method)
        for (insn in mn.instructions) {
            if (insn is LabelNode) {
                if (insn.previous == null) bb = nodeToBlock.getOrPut(insn, { bb })
                else {
                    bb = nodeToBlock.getOrPut(insn, { BodyBlock("%bb${bbc++}", method) })
                    if (!isTerminateInst(insn.previous)) {
                        val prev = nodeToBlock[insn.previous]
                        bb.addPredecessor(prev!!)
                        prev.addSuccessor(bb)
                    }
                }
            } else {
                bb = nodeToBlock.getOrPut(insn as AbstractInsnNode, { bb })
                if (insn is JumpInsnNode) {
                    if (insn.opcode != GOTO) {
                        val falseSuccessor = nodeToBlock.getOrPut(insn.next, { BodyBlock("%bb${bbc++}", method) })
                        bb.addSuccessor(falseSuccessor)
                        falseSuccessor.addPredecessor(bb)
                    }
                    val trueSuccessor = nodeToBlock.getOrPut(insn.label, { BodyBlock("%bb${bbc++}", method) })
                    bb.addSuccessor(trueSuccessor)
                    trueSuccessor.addPredecessor(bb)
                } else if (insn is TableSwitchInsnNode) {
                    val default = nodeToBlock.getOrPut(insn.dflt, { BodyBlock("%bb${bbc++}", method) })
                    bb.addSuccessors(default)
                    default.addPredecessor(bb)
                    for (lbl in insn.labels as MutableList<LabelNode>) {
                        val lblBB = nodeToBlock.getOrPut(lbl, { BodyBlock("%bb${bbc++}", method) })
                        bb.addSuccessors(lblBB)
                        lblBB.addPredecessor(bb)
                    }
                } else if (insn is LookupSwitchInsnNode) {
                    val default = nodeToBlock.getOrPut(insn.dflt, { BodyBlock("%bb${bbc++}", method) })
                    bb.addSuccessors(default)
                    default.addPredecessor(bb)
                    for (lbl in insn.labels as MutableList<LabelNode>) {
                        val lblBB = nodeToBlock.getOrPut(lbl, { BodyBlock("%bb${bbc++}", method) })
                        bb.addSuccessors(lblBB)
                        lblBB.addPredecessor(bb)
                    }
                }
            }
            method.addIfNotContains(bb)
        }
        for (insn in mn.tryCatchBlocks as MutableList<TryCatchBlockNode>) {
            val handle = getBasicBlock(insn.handler) as CatchBlock
            nodeToBlock[insn.handler] = handle
            val from = getBasicBlock(insn.start)
            val to = getBasicBlock(insn.end)
            method.getBlockRange(from, to).forEach {
                handle.addThrower(it)
                it.addHandler(handle)
            }
            method.addCatchBlock(handle)
        }
    }

    private fun buildFramesMap() {
        val modifiesMap = mutableMapOf<BasicBlock, MutableSet<Int>>()
        val readsMap = mutableMapOf<BasicBlock, MutableMap<Int, Boolean>>()
        for (insn in mn.instructions) {
            val bb = getBasicBlock(insn as AbstractInsnNode)
            val modifiesSet = modifiesMap.getOrPut(bb, { mutableSetOf() })
            val readMap = readsMap.getOrPut(bb, { mutableMapOf() })
            if (insn is VarInsnNode) {
                when (insn.opcode) {
                    in ISTORE..ASTORE -> {
                        modifiesSet.add(insn.`var`)
                        if (!readMap.containsKey(insn.`var`)) readMap[insn.`var`] = false
                    }
                    in ILOAD..ALOAD -> {
                        if (!readMap.containsKey(insn.`var`)) readMap[insn.`var`] = true
                    }
                    else -> {
                    }
                }
            }
        }
        for (bb in method.basicBlocks) {
            val readMap = readsMap[bb] ?: throw UnexpectedException("No read map for basic block ${bb.name}")
            val modifies = modifiesMap[bb] ?: throw UnexpectedException("No modifies map for basic block ${bb.name}")
            val reads = readMap.map { if (it.value) it.key else null }.filterNotNull()
            val sf = StackFrame(bb)
            sf.readedLocals.addAll(reads)
            sf.modifiedLocals.addAll(modifies)
            frames[bb] = sf
        }
    }

    private fun buildPhiInstructions() {
        for (it in frames) {
            val bb = it.key
            if (bb.predecessors.isEmpty()) continue

            val sf = it.value
            val predFrames = bb.predecessors.map { getFrame(it) }
            val stacks = predFrames.map { it.stack }
            val stackSizes = stacks.map { it.size }.toSet()
            require(stackSizes.size == 1, { "Stack sizes of ${bb.name} predecessors are different" })

            for ((indx, phi) in sf.stackPhis.withIndex()) {
                val incomings = predFrames.map { Pair(it.bb, it.stack[indx]) }.toMap()
                if (incomings.values.toSet().size > 1) {
                    val newPhi = IF.getPhi(phi.name, phi.type, incomings)
                    phi.replaceAllUsesWith(newPhi)
                    bb.replace(phi, newPhi)
                } else {
                    phi.replaceAllUsesWith(incomings.values.first())
                    bb.remove(phi)
                }
            }

            for ((local, phi) in sf.localPhis) {
                val incomings = predFrames
                        .map {
                            val value = it.locals[local]
                            require(value != null, { "No local $local defined for ${bb.name}" })
                            Pair(it.bb, value!!)
                        }.toMap()
                if (incomings.values.toSet().size > 1) {
                    val newPhi = IF.getPhi(phi.name, phi.type, incomings)
                    phi.replaceAllUsesWith(newPhi)
                    bb.replace(phi, newPhi)
                } else {
                    phi.replaceAllUsesWith(incomings.values.first())
                    bb.remove(phi)
                }
            }
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

        buildCFG()
        buildFramesMap()

        if (mn.instructions.first is LabelNode) {
            val entry = BodyBlock("entry", method)
            val oldEntry = method.getEntry()
            entry.addInstruction(IF.getJump(oldEntry))
            entry.addSuccessor(oldEntry)
            oldEntry.addPredecessor(entry)
            method.basicBlocks.add(0, entry)

            val sf = StackFrame(entry)
            sf.locals.putAll(locals)
            frames[entry] = sf
        }

        for (insn in mn.instructions) {
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
                else -> throw UnexpectedOpcodeException("Unknown insn: ${(insn as AbstractInsnNode).opcode}")
            }
        }

        buildPhiInstructions()
        return method
    }
}