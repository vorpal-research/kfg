package org.jetbrains.research.kfg.builder.cfg

import org.jetbrains.research.kfg.*
import org.jetbrains.research.kfg.builder.asm.AsmBuilder
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
        val incomingLocals = mutableSetOf<Int>()

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
        if (bb.predecessors.isEmpty()) {
            if (bb is CatchBlock) {
                // all locals for the catch block are the same, as for the try block entry
                val entry = bb.from()
                val sf = getFrame(entry)
                locals.clear()
                locals.putAll(sf.locals)

                // stack for catch block contains only reference to catched exception
                stack.clear()
                val inst = IF.getCatch(ST.getNextSlot(), bb.exception)
                bb.addInstruction(inst)
                stack.push(inst)
            }
        } else {
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
            for (local in sf.incomingLocals) {
                val type = predFrames.mapNotNull { it.locals[local] }.first().type
                val phi = IF.getPhi(ST.getNextSlot(), type, mapOf())
                bb.addInstruction(phi)
                locals[local] = phi
                sf.localPhis[local] = phi as PhiInst
            }
        }
    }

    private fun isTerminateInst(insn: AbstractInsnNode): Boolean {
        if (insn is TableSwitchInsnNode) return true
        if (insn is LookupSwitchInsnNode) return true
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
        val rhv = stack.pop()
        val lhv = stack.pop()
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
        val bb = getBasicBlock(insn)
        val `class` = CM.getByName(insn.owner)
        val method = `class`.getMethod(insn.name, insn.desc)
        val args = mutableListOf<Value>()
        method.argTypes.forEach {
            args.add(0, stack.pop())
        }
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
        val bb = getBasicBlock(insn)
        val falseSuccessor = getBasicBlock(insn.next)
        val trueSuccessor = getBasicBlock(insn.label)

        if (insn.opcode == GOTO) {
            reserveState(bb)
            bb.addInstruction(IF.getJump(trueSuccessor))
        } else {
            val name = ST.getNextSlot()
            val rhv = stack.pop()
            val opc = toCmpOpcode(insn.opcode)
            val cond = when (insn.opcode) {
                in IFEQ..IFLE -> IF.getCmp(name, opc, VF.getZeroConstant(rhv.type), rhv)
                in IF_ICMPEQ..IF_ACMPNE -> IF.getCmp(name, opc, stack.pop(), rhv)
                in IFNULL..IFNONNULL -> IF.getCmp(name, opc, rhv, VF.getNullConstant())
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
        val rhv = IF.getBinary(ST.getNextSlot(), BinaryOpcode.Add(), VF.getIntConstant(insn.incr), lhv)
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
        reserveState(bb)
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
        reserveState(bb)
        bb.addInstruction(IF.getSwitch(key, default, branches))
    }

    private fun convertMultiANewArrayInsn(insn: MultiANewArrayInsnNode) {
        val bb = getBasicBlock(insn)
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
        for (insn in method.mn.instructions) {
            if (insn is LabelNode) {
                if (insn.previous == null) { // when first instruction of method is label
                    bb = nodeToBlock.getOrPut(insn, { bb })
                    val entry = BodyBlock("entry", method)
                    entry.addInstruction(IF.getJump(bb))
                    entry.addSuccessor(bb)
                    bb.addPredecessor(entry)


                    val sf = frames.getOrPut(entry, { StackFrame(entry) })
                    sf.locals.putAll(locals)

                    method.addIfNotContains(entry)
                }
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
                }
            }
            method.addIfNotContains(bb)
        }
        for (insn in method.mn.tryCatchBlocks as MutableList<TryCatchBlockNode>) {
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

    private fun buildFrames() {
        val insideLocals = mutableMapOf<BasicBlock, MutableSet<Int>>()
        val incomingLocals = mutableMapOf<BasicBlock, MutableSet<Int>>()
        for (it in locals) {
            insideLocals.getOrPut(method.getEntry(), { mutableSetOf() }).add(it.key)
        }

        for (insn in method.mn.instructions) {
            val bb = getBasicBlock(insn as AbstractInsnNode)
            val definedSet = insideLocals.getOrPut(bb, { mutableSetOf() })
            if (insn is VarInsnNode) {
                when (insn.opcode) {
                    in ISTORE..ASTORE -> definedSet.add(insn.`var`)
                    in ILOAD..ALOAD -> definedSet.add(insn.`var`)
                    else -> {
                    }
                }
            }
        }

        val getAllLocals = { bb: BasicBlock ->
            val res = insideLocals[bb]?.toMutableSet()
                    ?: throw UnexpectedException("No inside locals map for basic block ${bb.name}")
            res.addAll(incomingLocals.getOrDefault(bb, mutableSetOf()))
            res
        }

        val (order, cycled) = TopologicalSorter().sort(method.getEntry())

        val cycledVisited = cycled.map { Pair(it as BasicBlock, false) }.toMap().toMutableMap()
        for (bb in order.map { it as BasicBlock }.reversed()) {
            if (bb in cycled) require(cycledVisited[bb]!! || bb == method.getEntry(), { "Cycle entry not visited" })

            if (bb.predecessors.isNotEmpty() && bb !in cycled) {
                val predecessorsLocals = bb.predecessors.map(getAllLocals)
                val incomingLocal = predecessorsLocals.fold(predecessorsLocals.first(), { acc, current ->
                    acc.intersect(current).toMutableSet()
                })
                incomingLocals[bb] = incomingLocal
            }
            bb.successors.filter { it in cycledVisited.keys }.filter { !cycledVisited[it]!! }.forEach {
                incomingLocals[it] = getAllLocals(bb)
                cycledVisited[it] = true
            }
        }

        for (bb in method.basicBlocks) {
            val incomings = incomingLocals.getOrPut(bb, { mutableSetOf() })
            val sf = frames.getOrPut(bb, { StackFrame(bb) })
            sf.incomingLocals.addAll(incomings)
        }
    }

    private fun buildPhiInstructions() {
        for ((bb, sf) in frames) {
            if (bb.predecessors.isEmpty()) continue

            val predFrames = bb.predecessors.map { getFrame(it) }
            val stacks = predFrames.map { it.stack }
            val stackSizes = stacks.map { it.size }.toSet()
            require(stackSizes.size == 1, { "Stack sizes of ${bb.name} predecessors are different" })

            for ((indx, phi) in sf.stackPhis.withIndex()) {
                val incomings = predFrames.map { Pair(it.bb, it.stack[indx]) }.toMap()
                val incomingValues = incomings.values.toSet()
                if (incomingValues.size > 1) {
                    val newPhi = IF.getPhi(phi.name, phi.type, incomings)
                    phi.replaceAllUsesWith(newPhi)
                    bb.replace(phi, newPhi)
                } else {
                    phi.replaceAllUsesWith(incomingValues.first())
                    bb.remove(phi)
                }
            }

            for ((local, phi) in sf.localPhis) {
                val incomings = predFrames.mapNotNull {
                    val value = it.locals[local]
                    if (value != null) Pair(it.bb, value) else null
                }.toMap()
                require(incomings.size == predFrames.size, { "Local $local is not defined for all $bb predecessors" })

                val incomingValues = incomings.values.toSet()
                if (incomingValues.size > 1) {
                    val type = mergeTypes(incomingValues.map { it.type }.toSet())
                    if (type == null) {
                        require(phi.getUsers().mapNotNull { it as? Instruction }.isEmpty(),
                                { "$method Incorrect phi ${phi.print()}  users" })
                        bb.remove(phi)
                    } else {
                        val newPhi = IF.getPhi(phi.name, type, incomings)
                        phi.replaceAllUsesWith(newPhi)
                        bb.replace(phi, newPhi)
                    }
                } else {
                    phi.replaceAllUsesWith(incomingValues.first())
                    bb.remove(phi)
                }
            }
        }
        for (inst in method.flatten().reversed()) {
            if (inst is PhiInst) {
                val bb = inst.parent ?: throw UnexpectedException("Instruction ${inst.print()} does not have parent")
                val instUsers = inst.getUsers().mapNotNull { it as? Instruction }
                if (instUsers.isEmpty()) {
                    bb.remove(inst)
                    continue
                }

                val incomings = inst.getIncomingValues().toSet()
                if (inst in incomings && incomings.size == 2) {
                    if (inst == incomings.first()) inst.replaceAllUsesWith(incomings.last())
                    if (inst == incomings.last()) inst.replaceAllUsesWith(incomings.first())
                    bb.remove(inst)
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

        // if the first instruction of method is label, add special BB before it, so method entry have no predecessors
        buildCFG()  // build basic blocks graph

        println(method)
        val nodes = method.basicBlocks.map { it as GraphNode }.toSet()
        val domTree = DominatorTreeBuilder(nodes).build()

        for (it in domTree) {
            val bb = it.key as BasicBlock
            println("${bb.name} idom is ${(it.value.idom.value as BasicBlock).name}")
        }
        println()

        buildFrames() // build frame maps for each basic block

        for (insn in method.mn.instructions) {
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

        method.slottracker.rerun()
        return method
    }
}