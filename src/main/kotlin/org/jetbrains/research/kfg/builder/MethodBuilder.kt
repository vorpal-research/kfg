package org.jetbrains.research.kfg.builder

import org.jetbrains.research.kfg.*
import org.jetbrains.research.kfg.ir.*
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.type.*
import org.objectweb.asm.commons.JSRInlinerAdapter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import java.util.*

class MethodBuilder(val method: Method, val mn: MethodNode)
    : JSRInlinerAdapter(Opcodes.ASM5, mn, mn.access, mn.name, mn.desc, mn.signature, mn.exceptions.map { it as String }.toTypedArray()) {
    val CM = ClassManager.instance
    val TF = TypeFactory.instance
    val VF = ValueFactory.instance
    val ST = method.slottracker
    val IF = InstructionFactory.instance

    inner class StackFrame {
        val inputs = mutableSetOf<BasicBlock>()
        val trackedLocals = mutableSetOf<Int>()
        val localsMap = mutableMapOf<Int, MutableMap<BasicBlock, Value>>()
        val stackMap = mutableMapOf<Value, MutableMap<BasicBlock, Value>>()
        private var out: MutableList<Value>? = null

        fun contains(bb: BasicBlock) = inputs.contains(bb)
        fun add(bb: BasicBlock) {
            inputs.add(bb)
        }

        fun reserve(): List<Value> {
            if (out == null) {
                val newOut = mutableListOf<Value>()
                val currentStack = stack.toTypedArray()
                currentStack.mapTo(newOut) { VF.getLocal(ST.getNextSlot(), it.type) }
                out = newOut
            }
            return out?.toList() ?: throw UnexpectedException("Stack frame has no reserved values")
        }
    }

    private val locals = mutableMapOf<Int, Value>()
    private val nodeToBlock = mutableMapOf<AbstractInsnNode, BasicBlock>()
    private val frames = mutableSetOf<StackFrame>()
    private val stack = Stack<Value>()

    private fun getFrameUnsafe(bb: BasicBlock): StackFrame? {
        return frames.firstOrNull { it.contains(bb) }
    }

    private fun getFrame(bb: BasicBlock): StackFrame {
        val unsf = getFrameUnsafe(bb)
        if (unsf != null) return unsf
        val sf = StackFrame()
        sf.add(bb)
        frames.add(sf)
        return sf
    }

    private fun reserveStack(bb: BasicBlock) {
        val sf = getFrame(bb)
        val reserved = sf.reserve()
        for (`var` in reserved) {
            val stackMap = sf.stackMap.getOrPut(`var`, { mutableMapOf() })
            stackMap[bb] = stack.pop()
        }
        reserved.reversed().forEach { stack.push(it) }
        for (it in sf.trackedLocals) {
            val localMap = sf.localsMap.getOrPut(it, { mutableMapOf() })
            localMap[bb] = locals[it] ?: throw UnexpectedException("Unknown local var $it in basick block ${bb.name}")
        }
    }

    private fun recoverStack(bb: BasicBlock) {
        if (bb.predecessors.isEmpty()) return
        val sf = getFrame(bb.predecessors.first())
        stack.clear()
        for (it in sf.trackedLocals) {
            val localMap = sf.localsMap[it] ?: throw UnexpectedException("Locals map is null for ${bb.name}")
            val valueSet = localMap.values.toSet()
            if (valueSet.size > 1) {
                val phi = IF.getPhi(ST.getNextSlot(), valueSet.first().type, localMap)
                bb.addInstruction(phi)
                locals[it] = phi
            }
        }
        for (it in sf.stackMap) {
            val phi = IF.getPhi(ST.getNextSlot(), it.value.values.first().type, it.value)
            bb.addInstruction(phi)
            stack.push(phi)
        }
    }

    private fun isTerminateInst(insn: AbstractInsnNode): Boolean {
        if (insn is JumpInsnNode && insn.opcode == GOTO) return true
        if (insn is InsnNode && insn.opcode in IRETURN..RETURN) return true
        if (insn is InsnNode && insn.opcode == ATHROW) return true
        return false
    }

    private fun getBasicBlock(insn: AbstractInsnNode) = nodeToBlock[insn] ?: throw UnexpectedException("Unknown node $insn")

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
            else -> InvalidOpcodeException("$opcode in TypeInsn")
        }
    }

    private fun convertFieldInsn(insn: FieldInsnNode) {
        val bb = getBasicBlock(insn)
        val opcode = insn.opcode
        val fieldType = parseDesc(insn.desc)
        val klass = CM.createOrGet(insn.owner)
        when (opcode) {
            GETSTATIC -> {
                val inst = IF.getFieldLoad(ST.getNextSlot(), VF.getField(insn.name, klass, fieldType))
                bb.addInstruction(inst)
                stack.push(inst)
            }
            PUTSTATIC -> {
                val field = VF.getField(insn.name, klass, fieldType)
                val value = stack.pop()
                bb.addInstruction(IF.getFieldStore(field, value))
            }
            GETFIELD -> {
                val obj = stack.pop()
                val inst = IF.getFieldLoad(ST.getNextSlot(), VF.getField(insn.name, klass, fieldType))
                bb.addInstruction(inst)
                stack.push(inst)
            }
            PUTFIELD -> {
                val value = stack.pop()
                val obj = stack.pop()
                val field = VF.getField(insn.name, klass, fieldType, obj)
                bb.addInstruction(IF.getFieldStore(field, value))
            }
        }
    }

    private fun convertMethodInsn(insn: MethodInsnNode) {
        val bb = getBasicBlock(insn)
        val klass = CM.createOrGet(insn.owner)
        val method = klass.getMethod(insn.name, insn.desc)
        val args = mutableListOf<Value>()
        method.arguments.forEach {
            args.add(stack.pop())
        }
        val returnType = method.retType
        val call =
                if (returnType.isVoid()) {
                    when (insn.opcode) {
                        INVOKESTATIC -> IF.getCall(method, klass, args.toTypedArray())
                        in arrayOf(INVOKEVIRTUAL, INVOKESPECIAL, INVOKEINTERFACE) -> {
                            val obj = stack.pop()
                            IF.getCall(method, klass, obj, args.toTypedArray())
                        }
                        else -> throw UnexpectedOpcodeException("Method insn opcode ${insn.opcode}")
                    }
                } else {
                    when (insn.opcode) {
                        INVOKESTATIC -> IF.getCall(ST.getNextSlot(), method, klass, args.toTypedArray())
                        in arrayOf(INVOKEVIRTUAL, INVOKESPECIAL, INVOKEINTERFACE) -> {
                            val obj = stack.pop()
                            IF.getCall(ST.getNextSlot(), method, klass, obj, args.toTypedArray())
                        }
                        else -> throw UnexpectedOpcodeException("Method insn opcode ${insn.opcode}")
                    }
                }
        bb.addInstruction(call)
        if (!returnType.isVoid()) {
            stack.push(call)
        }
    }

    private fun convertInvokeDynamicInsn(insn: InvokeDynamicInsnNode) {
        val klass = CM.createOrGet(insn.bsm.name)
        val method = klass.getMethod(insn.name, insn.desc)
        TODO()
    }

    private fun convertJumpInsn(insn: JumpInsnNode) {
        val bb = getBasicBlock(insn)
        val falseSuccessor = getBasicBlock(insn.next)
        val trueSuccessor = getBasicBlock(insn.label)

        if (insn.opcode == GOTO) {
            reserveStack(bb)
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
            reserveStack(bb)
            bb.addInstruction(cond)
            bb.addInstruction(IF.getBranch(cond, trueSuccessor, falseSuccessor))
        }
    }

    private fun convertLabel(lbl: LabelNode) {
        val bb = getBasicBlock(lbl)
        // if we came to this label not by jump, but just by consistently executing instructions
        if (lbl.previous != null && !isTerminateInst(lbl.previous)) {
            val prev = getBasicBlock(lbl.previous)
            reserveStack(prev)
            prev.addInstruction(IF.getJump(bb))
            bb.addPredecessor(prev)
            prev.addSuccessor(bb)
        }
        recoverStack(bb)
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
                val klass = CM.getClassByName(cst.owner)
                        ?: throw InvalidOperandException("Class ${cst.owner}")
                val method = klass.getMethod(cst.name, cst.desc)
                stack.push(VF.getMethodConstant(method))
            }
            else -> throw InvalidOperandException("Unknown object $cst")
        }
    }

    private fun convertIincInsn(insn: IincInsnNode) {
        val bb = getBasicBlock(insn)
        val lhv = locals[insn.`var`] ?: throw InvalidOperandException("${insn.`var`} local is invalid")
        val rhv = IF.getBinary(ST.getNextSlot(), BinaryOpcode.ADD, lhv, VF.getIntConstant(insn.incr))
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
        for (it in method.basicBlocks.reversed()) {
            if (it.predecessors.isEmpty()) continue
            if (it is CatchBlock) continue
            val sfl = it.predecessors.map { getFrameUnsafe(it) }.filterNotNull()
            if (sfl.size > 1) throw UnexpectedException("BB successors belong to different frames")
            val sf = sfl.firstOrNull() ?: getFrame(it.predecessors.first())
            sf.inputs.addAll(it.predecessors)
        }
    }

    private fun checkLocals() {
        val readsMap = mutableMapOf<BasicBlock, MutableMap<Int, Boolean>>()
        for (insn in mn.instructions) {
            val bb = getBasicBlock(insn as AbstractInsnNode)
            val bbMap = readsMap.getOrPut(bb, { mutableMapOf() })
            if (insn is VarInsnNode) {
                when (insn.opcode) {
                    in ISTORE..ASTORE -> if (!bbMap.containsKey(insn.`var`)) bbMap[insn.`var`] = false
                    in ILOAD..ALOAD -> {
                        if (!bbMap.containsKey(insn.`var`)) bbMap[insn.`var`] = true
                    }
                    else -> {}
                }
            }
        }
        for (bb in method.basicBlocks) {
            val bbMap = readsMap[bb] ?: throw UnexpectedException("No read map for basic block ${bb.name}")
            val readVals = bbMap.map { if (it.value) it.key else null }.filterNotNull()
            if (bb.predecessors.isNotEmpty()) getFrame(bb.predecessors.first()).trackedLocals.addAll(readVals)
        }
    }

    fun convert() {
        var localIndx = 0
        if (!method.isStatic()) locals[localIndx++] = VF.getThis(TF.getRefType(method.classRef))
        for ((indx, it) in method.arguments.withIndex()) {
            locals[localIndx++] = VF.getArgument("arg$$indx", method, it)
        }

        buildCFG()
        buildFramesMap()
        checkLocals()

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

        println(method.print())
        println()
    }
}