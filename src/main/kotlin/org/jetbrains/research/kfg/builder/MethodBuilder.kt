package org.jetbrains.research.kfg.builder

import org.jetbrains.research.kfg.InvalidOpcodeException
import org.jetbrains.research.kfg.InvalidOperandException
import org.jetbrains.research.kfg.InvalidTypeDescException
import org.jetbrains.research.kfg.UnexpectedOpcodeException
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.ClassManager
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.instruction.Instruction
import org.jetbrains.research.kfg.ir.instruction.InstructionFactory
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.type.parseDesc
import org.jetbrains.research.kfg.type.parsePrimaryType
import org.jetbrains.research.kfg.value.*
import org.jetbrains.research.kfg.value.expr.BinaryOpcode
import org.jetbrains.research.kfg.value.expr.UnaryOpcode
import org.objectweb.asm.commons.JSRInlinerAdapter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.*
import java.util.*

class MethodBuilder(val method: Method, val mn: MethodNode)
    : JSRInlinerAdapter(Opcodes.ASM5, mn, method.modifiers, method.name, mn.desc, mn.signature, mn.exceptions.map { it as String }.toTypedArray()) {
    val CM = ClassManager.instance
    val TF = TypeFactory.instance
    val VF = ValueFactory.instance
    val EF = VF.exprFactory
    val IF = InstructionFactory.instance

    private val locals = mutableMapOf<Int, Value>()
    private val labels = mutableMapOf<LabelNode, BasicBlock>()
    private val stack = Stack<Value>()
    private var currentBlock = BasicBlock("entry", method)
    private var numLocals = mn.maxLocals
    private var bbc = 0

    private fun newLocal(type: Type): Value = VF.getLocal(numLocals++, type)

    private fun addInst(i: Instruction) {
        currentBlock.instructions.add(i)
        i.bb = currentBlock
    }

    private fun getBasicBlockByLabel(lbl: LabelNode): BasicBlock {
        if (labels.contains(lbl)) {
            return labels[lbl]!!
        } else {
            val bb = BasicBlock("%bb${bbc++}", method)
            labels[lbl] = bb
            return bb
        }
    }

    private fun addBasicBlock(bb: BasicBlock) {
        method.addIfNotContains(currentBlock)
        currentBlock = bb
    }

    private fun convertConst(opcode: Int) {
        val cnst = when {
            opcode == ACONST_NULL -> VF.getNullConstant()
            opcode in ICONST_0..ICONST_5 -> VF.getIntConstant(opcode - ICONST_0)
            opcode in LCONST_0..LCONST_1 -> VF.getLongConstant((opcode - LCONST_0).toLong())
            opcode in FCONST_0..FCONST_2 -> VF.getFloatConstant((opcode - FCONST_0).toFloat())
            opcode in DCONST_0..DCONST_1 -> VF.getDoubleConstant((opcode - DCONST_0).toDouble())
            else -> throw UnexpectedOpcodeException("Unknown const $opcode")
        }
        stack.push(cnst)
    }

    private fun convertArrayLoad(opcode: Int) {
        val index = stack.pop()
        val arrayRef = stack.pop()
        stack.push(EF.getArrayLoad(arrayRef, index))
    }

    private fun convertArrayStore(opcode: Int) {
        val value = stack.pop()
        val index = stack.pop()
        val array = stack.pop()
        addInst(IF.getStore(array, index, value))
    }

    private fun convertPop(opcode: Int) {
        when (opcode) {
            POP -> stack.pop()
            POP2 -> {
                val top = stack.pop()
                if (!top.type.isDWord()) stack.pop()
            }
            else -> throw UnexpectedOpcodeException("Pop opcode $opcode")
        }
    }

    private fun convertDup(opcode: Int) {
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
            DUP2_X2 ->  {
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

    private fun convertSwap(opcode: Int) {
        val top = stack.pop()
        val bot = stack.pop()
        stack.push(top)
        stack.push(bot)
    }

    private fun convertBinary(opcode: Int) {
        val lhv = stack.pop()
        val rhv = stack.pop()
        val binOp = toBinaryOpcode(opcode)
        stack.push(EF.getBinary(binOp, lhv, rhv))
    }

    private fun convertUnary(opcode: Int) {
        val array = stack.pop()
        val op = when (opcode) {
            in INEG .. DNEG -> UnaryOpcode.NEG
            ARRAYLENGTH -> UnaryOpcode.LENGTH
            else -> throw InvalidOperandException("Unary opcode $opcode")
        }
        stack.push(EF.getUnary(op, array))
    }

    private fun convertCast(opcode: Int) {
        val op = stack.pop()
        val type = when (opcode) {
            in arrayOf(I2L, F2L, D2L) -> TF.getLongType()
            in arrayOf(I2F, L2F, D2F) -> TF.getFloatType()
            in arrayOf(I2D, L2D, F2D) -> TF.getDoubleType()
            in arrayOf(L2I, F2I, D2I) -> TF.getIntType()
            I2B -> TF.getByteType()
            I2C -> TF.getCharType()
            I2S -> TF.getShortType()
            else -> throw UnexpectedOpcodeException("Cast opcode $opcode")
        }
        stack.push(EF.getCast(type, op))
    }

    private fun convertCmp(opcode: Int) {
        val lhv = stack.pop()
        val rhv = stack.pop()
        val op = toCmpOpcode(opcode)
        stack.push(EF.getCmp(op, lhv, rhv))
    }

    private fun convertReturn(opcode: Int) {
        if (opcode == RETURN) {
            addInst(IF.getReturn())
        } else {
            val retval = stack.pop()
            addInst(IF.getReturn(retval))
        }
    }

    private fun convertLocalLoad(opcode: Int, `var`: Int) {
        stack.push(locals[`var`])
    }

    private fun convertLocalStore(opcode: Int, `var`: Int) {
        val obj = stack.pop()
        val local = locals.getOrPut(`var`, { newLocal(obj.type) })
        addInst(IF.getAssign(local, obj))
    }

    private fun convertMonitor(opcode: Int) {
        val owner = stack.pop()
        when (opcode) {
            MONITORENTER -> addInst(IF.getEnterMonitor(owner))
            MONITOREXIT -> addInst(IF.getExitMonitor(owner))
            else -> throw UnexpectedOpcodeException("Monitor opcode $opcode")
        }
    }

    private fun convertThrow(opcode: Int) {
        val throwable = stack.pop()
        addInst(IF.getThrow(throwable))
    }

    private fun convertInsn(insn: InsnNode) {
        val opcode = insn.opcode
        when (opcode) {
            NOP -> {}
            in ACONST_NULL..DCONST_1 -> convertConst(opcode)
            in IALOAD..SALOAD -> convertArrayLoad(opcode)
            in IASTORE..SASTORE -> convertArrayStore(opcode)
            in POP .. POP2 -> convertPop(opcode)
            in DUP .. DUP2_X2 -> convertDup(opcode)
            SWAP -> convertSwap(opcode)
            in IADD .. DREM -> convertBinary(opcode)
            in INEG .. DNEG -> convertUnary(opcode)
            in ISHL .. LXOR -> convertBinary(opcode)
            in I2L .. I2S -> convertCast(opcode)
            in LCMP .. DCMPG -> convertCmp(opcode)
            in IRETURN .. RETURN -> convertReturn(opcode)
            ARRAYLENGTH -> convertUnary(opcode)
            ATHROW -> convertThrow(opcode)
            in MONITORENTER .. MONITOREXIT -> convertMonitor(opcode)
            else -> throw UnexpectedOpcodeException("Insn opcode $opcode")
        }
    }

    private fun convertIntInsn(insn: IntInsnNode) {
        val opcode = insn.opcode
        val operand = insn.operand
        when (opcode) {
            BIPUSH -> stack.push(VF.getIntConstant(operand))
            SIPUSH -> stack.push(VF.getIntConstant(operand))
            NEWARRAY -> {
                val type = parsePrimaryType(operand)
                val count = stack.pop()
                val rhv = EF.getNewArray(type, count)
                val lhv = newLocal(type)
                addInst(IF.getAssign(lhv, rhv))
                stack.push(lhv)
            }
            else -> throw UnexpectedOpcodeException("IntInsn opcode $opcode")
        }
    }

    private fun convertVarInsn(insn: VarInsnNode) {
        val opcode = insn.opcode
        val `var` = insn.`var`
        when (opcode) {
            in ISTORE .. ASTORE -> convertLocalStore(opcode, `var`)
            in ILOAD .. ALOAD -> convertLocalLoad(opcode, `var`)
            RET -> TODO()
            else -> throw UnexpectedOpcodeException("VarInsn opcode $opcode")
        }
    }

    private fun convertTypeInsn(insn: TypeInsnNode) {
        val opcode = insn.opcode
        val type = try {
            parseDesc(insn.desc)
        } catch (e: InvalidTypeDescException) {
            TF.getRefType(desc)
        }
        when (opcode) {
            NEW -> {
                val rhv = EF.getNew(type)
                val lhv = newLocal(type)
                addInst(IF.getAssign(lhv, rhv))
                stack.push(lhv)
            }
            ANEWARRAY -> {
                val count = stack.pop()
                val rhv = EF.getNewArray(type, count)
                val lhv = newLocal(rhv.type)
                addInst(IF.getAssign(lhv, rhv))
                stack.push(lhv)
            }
            CHECKCAST -> {
                val castable = stack.pop()
                stack.push(EF.getCheckCast(type, castable))
            }
            INSTANCEOF -> {
                val obj = stack.pop()
                stack.push(EF.getInstanceOf(obj))
            }
            else -> InvalidOpcodeException("$opcode in TypeInsn")
        }
    }

    private fun convertFieldInsn(insn: FieldInsnNode) {
        val opcode = insn.opcode
        val fieldType = parseDesc(insn.desc)
        val klass = CM.createOrGet(insn.owner)
        when (opcode) {
            GETSTATIC -> {
                stack.push(VF.getField(insn.name, klass, fieldType))
            }
            PUTSTATIC -> {
                val field = VF.getField(insn.name, klass, fieldType)
                val value = stack.pop()
                addInst(IF.getAssign(field, value))
            }
            GETFIELD -> {
                val obj = stack.pop()
                stack.push(VF.getField(insn.name, klass, fieldType, obj))
            }
            PUTFIELD -> {
                val value = stack.pop()
                val obj = stack.pop()
                val field = VF.getField(insn.name, klass, fieldType, obj)
                addInst(IF.getAssign(field, value))
            }
        }
    }

    private fun convertMethodInsn(insn: MethodInsnNode) {
        val klass = CM.createOrGet(insn.owner)
        val method = klass.createOrGet(insn.name, insn.desc)
        val args = mutableListOf<Value>()
        method.arguments.forEach {
            args.add(stack.pop())
        }
        val returnType = method.retType
        val call = when (insn.opcode) {
            INVOKESTATIC -> EF.getCall(returnType, method, klass, args.toTypedArray())
            in arrayOf(INVOKEVIRTUAL, INVOKESPECIAL, INVOKEINTERFACE) -> {
                val obj = stack.pop()
                EF.getCall(returnType, method, klass, obj, args.toTypedArray())
            }
            else -> throw UnexpectedOpcodeException("Method insn opcode ${insn.opcode}")
        }
        if (returnType.isVoid()) {
            addInst(IF.getCall(call))
        } else {
            val lhv = newLocal(returnType)
            addInst(IF.getCall(lhv, call))
            stack.push(lhv)
        }
    }

    private fun convertInvokeDynamicInsn(insn: InvokeDynamicInsnNode) {
        super.visitInvokeDynamicInsn(name, desc, insn.bsm, insn.bsmArgs)

        val klass = CM.createOrGet(insn.bsm.name)
        val method = klass.getMethod(name) ?: throw InvalidOperandException("Unknown method $name of class ${insn.bsm.owner}")
        TODO()
    }

    private fun convertJumpInsn(insn: JumpInsnNode) {
        val trueSuccessor = getBasicBlockByLabel(insn.label)
        val nextInsn = insn.next
        val falseSuccessor =
                if (nextInsn is LabelNode) getBasicBlockByLabel(nextInsn)
                else BasicBlock("%bb${bbc++}", method)

        if (insn.opcode == GOTO) {
            addInst(IF.getJump(trueSuccessor))
            currentBlock.addSuccessor(trueSuccessor)
            trueSuccessor.addPredecessor(currentBlock)
        } else {
            val lhv = stack.pop()
            val opc = toCmpOpcode(insn.opcode)
            val cond = when (insn.opcode) {
                in IFEQ .. IFLE -> EF.getCmp(opc, lhv, VF.getZeroConstant(lhv.type))
                in IF_ICMPEQ .. IF_ACMPNE -> EF.getCmp(opc, lhv, stack.pop())
                in IFNULL .. IFNONNULL -> EF.getCmp(opc, lhv, VF.getNullConstant())
                else -> throw UnexpectedOpcodeException("Jump opcode ${insn.opcode}")
            }
            addInst(IF.getBranch(cond, trueSuccessor, falseSuccessor))
            currentBlock.addSuccessor(trueSuccessor)
            trueSuccessor.addPredecessor(currentBlock)
            currentBlock.addSuccessor(falseSuccessor)
            falseSuccessor.addPredecessor(currentBlock)
        }

        addBasicBlock(falseSuccessor)
    }

    private fun convertLabel(lbl: LabelNode) {
        val next = getBasicBlockByLabel(lbl)
        if (currentBlock.isNotEmpty() && !currentBlock.back().isTerminate() && currentBlock.successors.isEmpty()) {
            addInst(IF.getJump(next))
            currentBlock.addSuccessor(next)
            next.addPredecessor(currentBlock)
        }
        addBasicBlock(next)
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
                val method = klass.getMethod(cst.name)
                        ?: throw InvalidOperandException("Method ${cst.name} in ${cst.owner}")
                stack.push(VF.getMethodConstant(method))
            }
            else -> throw InvalidOperandException("Unknown object $cst")
        }
    }

    private fun convertIincInsn(insn: IincInsnNode) {
        val lhv = locals[insn.`var`] ?: throw InvalidOperandException("${insn.`var`} local is invalid")
        val rhv = EF.getBinary(BinaryOpcode.ADD, lhv, VF.getIntConstant(insn.incr))
        addInst(IF.getAssign(lhv, rhv))
    }

    private fun convertTableSwitchInsn(insn: TableSwitchInsnNode) {
        TODO()
    }

    private fun convertLookupSwitchInsn(insn: LookupSwitchInsnNode) {
        val default = getBasicBlockByLabel(insn.dflt)
        val branches = mutableMapOf<Value, BasicBlock>()
        for (i in 0 .. insn.keys.size) {
            branches[VF.getIntConstant(insn.keys[i] as Int)] = getBasicBlockByLabel(insn.labels[i] as LabelNode)
        }
        addInst(IF.getSwitch(default, branches))
    }

    private fun convertMultiANewArrayInsn(insn: MultiANewArrayInsnNode) {
        super.visitMultiANewArrayInsn(insn.desc, insn.dims)
        val type = parseDesc(insn.desc)
        stack.push(newLocal(type))
    }

    private fun convertTryCatchBlock(insn: TryCatchBlockNode) {
        val from = getBasicBlockByLabel(insn.start)
        val to = getBasicBlockByLabel(insn.end)
        val hndlr = getBasicBlockByLabel(insn.handler)
        val tp = TF.getRefType(insn.type)
        method.getBlockRange(from, to).forEach { it.addHandler(tp, hndlr) }
    }

    fun convert() {
        var indx = 0
        if (!method.isStatic()) locals[indx] = VF.getLocal(indx++, TF.getRefType(method.classRef))
        for (it in method.arguments) {
            locals[indx] = VF.getLocal(indx++, it)
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
                is TryCatchBlockNode -> convertTryCatchBlock(insn)
                else -> throw UnexpectedOpcodeException("Unknown insn: ${(insn as AbstractInsnNode).opcode}")
            }
        }

        method.addIfNotContains(currentBlock)

        println(method.print())
        println()
    }
}