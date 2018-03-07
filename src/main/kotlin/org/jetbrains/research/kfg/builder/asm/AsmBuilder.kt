package org.jetbrains.research.kfg.builder.asm

import org.jetbrains.research.kfg.*
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.type.*
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.*

fun typeToFullInt(type: Type) = when (type) {
    is BoolType -> 0
    is ByteType -> 5
    is ShortType -> 7
    is CharType -> 6
    is IntType -> 0
    is LongType -> 1
    is FloatType -> 2
    is DoubleType -> 3
    is Reference -> 4
    else -> throw UnexpectedException("Unexpected type for conversion: ${type.getName()}")
}

fun typeToInt(type: Type) = when (type) {
    is LongType -> 1
    is Integral -> 0
    is FloatType -> 2
    is DoubleType -> 3
    is Reference -> 4
    else -> throw UnexpectedException("Unexpected type for conversion: ${type.getName()}")
}

class AsmBuilder(method: Method) : MethodVisitor(method) {
    val insnList = InsnList()
    val labels = method.basicBlocks.map { Pair(it, LabelNode()) }.toMap()
    val stack = mutableListOf<Value>()
    val locals = mutableMapOf<Value, Int>()
    var maxLocals = 0

    init {
        if (!method.isStatic()) {
            val `this` = VF.getThis(TF.getRefType(method.`class`))
            locals[`this`] = getLocalFor(`this`)
        }
        for ((indx, type) in method.argTypes.withIndex()) {
            val arg = VF.getArgument("arg$$indx", method, type)
            locals[arg] = getLocalFor(arg)
        }
    }

    private fun stackPop() = stack.removeAt(stack.size)
    private fun stackPush(value: Value) = stack.add(value)

    private fun getLocalFor(value: Value) = locals[value]
            ?: if (value.type.isDWord()) {
                val old = maxLocals
                maxLocals += 2
                old
            } else {
                maxLocals++
            }

    private fun getLabel(bb: BasicBlock) = labels[bb] ?: throw UnexpectedException("Unknown basic block ${bb.name}")

    private fun convertConstantToInsn(`const`: Constant) = when (`const`) {
        is BoolConstant -> InsnNode(if (`const`.value) ICONST_1 else ICONST_0)
        is ByteConstant -> IntInsnNode(BIPUSH, `const`.value.toInt())
        is ShortConstant -> IntInsnNode(SIPUSH, `const`.value.toInt())
        is IntConstant -> when (`const`.value) {
            in -1..5 -> InsnNode(ICONST_0 + `const`.value)
            in Byte.MIN_VALUE..Byte.MAX_VALUE -> IntInsnNode(BIPUSH, `const`.value)
            in Short.MIN_VALUE..Short.MAX_VALUE -> IntInsnNode(SIPUSH, `const`.value)
            else -> LdcInsnNode(`const`.value)
        }
        is LongConstant -> when (`const`.value) {
            in 0..1 -> InsnNode(LCONST_0 + `const`.value.toInt())
            else -> LdcInsnNode(`const`.value)
        }
        is FloatConstant -> when (`const`.value) {
            0.0F -> InsnNode(FCONST_0)
            1.0F -> InsnNode(FCONST_1)
            2.0F -> InsnNode(FCONST_2)
            else -> LdcInsnNode(`const`.value)
        }
        is DoubleConstant -> when (`const`.value) {
            0.0 -> InsnNode(DCONST_0)
            1.0 -> InsnNode(DCONST_1)
            else -> LdcInsnNode(`const`.value)
        }
        is StringConstant -> LdcInsnNode(`const`.value)
        is ClassConstant -> LdcInsnNode(org.objectweb.asm.Type.getType(`const`.type.getAsmDesc()))
        is MethodConstant -> TODO()
        else -> throw UnexpectedException("Unknown constant type $`const`")
    }

    private fun addOperandsToStack(operands: Array<Value>) {
        while (stack.isNotEmpty()) {
            val operand = stackPop()
            val local = getLocalFor(operand)
            val opcode = ISTORE + typeToInt(operand.type)
            val insn = IntInsnNode(opcode, local)
            insnList.add(insn)
        }
        for (operand in operands) {
            val insn = when (operand) {
                is Constant -> convertConstantToInsn(operand)
                else -> {
                    val local = getLocalFor(operand)
                    val opcode = ILOAD + typeToInt(operand.type)
                    IntInsnNode(opcode, local)
                }
            }
            insnList.add(insn)
            stackPush(operand)
        }
    }

    override fun visitBasicBlock(bb: BasicBlock) {
        insnList.add(getLabel(bb))
        super.visitBasicBlock(bb)
    }

    override fun visitArrayLoadInst(inst: ArrayLoadInst) {
        addOperandsToStack(inst.operands)
        val opcode = IALOAD + typeToFullInt(inst.type)
        val insn = InsnNode(opcode)
        insnList.add(insn)
        inst.operands.forEach { stackPop() }
        stackPush(inst)
    }

    override fun visitArrayStoreInst(inst: ArrayStoreInst) {
        addOperandsToStack(inst.operands)
        val opcode = IASTORE + typeToFullInt(inst.type)
        val insn = InsnNode(opcode)
        insnList.add(insn)
        inst.operands.forEach { stackPop() }
    }

    override fun visitBinaryInst(inst: BinaryInst) {
        addOperandsToStack(inst.operands)
        val opcode = inst.opcode.toAsmOpcode() + typeToInt(inst.type)
        val insn = InsnNode(opcode)
        insnList.add(insn)
        inst.operands.forEach { stackPop() }
        stackPush(inst)
    }

    override fun visitJumpInst(inst: JumpInst) {
        val successor = getLabel(inst.successor)
        val insn = JumpInsnNode(GOTO, successor)
        insnList.add(insn)
    }

    override fun visitNewInst(inst: NewInst) {
        val insn = TypeInsnNode(NEW, inst.type.getAsmDesc())
        insnList.add(insn)
        stackPush(inst)
    }

    override fun visitReturnInst(inst: ReturnInst) {
        addOperandsToStack(inst.operands)
        val opcode = if (inst.hasReturnValue()) RETURN else IRETURN + typeToInt(inst.getReturnType())
        val insn = InsnNode(opcode)
        insnList.add(insn)
    }

    override fun visitBranchInst(inst: BranchInst) {
        addOperandsToStack(inst.operands)
        val trueLabel = getLabel(inst.trueSuccessor)
        val falseLabel = getLabel(inst.falseSuccessor)
        TODO()
    }

    override fun visitCastInst(inst: CastInst) {
        addOperandsToStack(inst.operands)
        val originalType = inst.getOperand().type
        val targetType = inst.type
        val insn = if (originalType.isPrimary() and targetType.isPrimary()) {
            val opcode = when (originalType) {
                is IntType -> when (targetType) {
                    is LongType -> I2L
                    is FloatType -> I2F
                    is DoubleType -> I2D
                    is ByteType -> I2B
                    is CharType -> I2C
                    is ShortType -> I2S
                    else -> throw InvalidOperandException("Invalid cast from ${originalType.getName()} to ${targetType.getName()}")
                }
                is LongType -> when (targetType) {
                    is IntType -> L2I
                    is FloatType -> L2F
                    is DoubleType -> L2D
                    else -> throw InvalidOperandException("Invalid cast from ${originalType.getName()} to ${targetType.getName()}")
                }
                is FloatType -> when (targetType) {
                    is IntType -> F2I
                    is LongType -> F2L
                    is DoubleType -> F2D
                    else -> throw InvalidOperandException("Invalid cast from ${originalType.getName()} to ${targetType.getName()}")
                }
                is DoubleType -> when (targetType) {
                    is IntType -> D2I
                    is LongType -> D2L
                    is FloatType -> D2F
                    else -> throw InvalidOperandException("Invalid cast from ${originalType.getName()} to ${targetType.getName()}")
                }
                else -> throw InvalidOperandException("Invalid cast from ${originalType.getName()} to ${targetType.getName()}")
            }
            InsnNode(opcode)
        } else {
            TypeInsnNode(CHECKCAST, targetType.getAsmDesc())
        }
        insnList.add(insn)
        inst.operands.forEach { stackPop() }
        stackPush(inst)
    }

    override fun visitEnterMonitorInst(inst: EnterMonitorInst) {
        val insn = InsnNode(MONITORENTER)
        insnList.add(insn)
    }

    override fun visitExitMonitorInst(inst: ExitMonitorInst) {
        val insn = InsnNode(MONITOREXIT)
        insnList.add(insn)
    }

    override fun visitNewArrayInst(inst: NewArrayInst) {
        addOperandsToStack(inst.operands)
        val component = inst.compType
        val insn = if (component.isPrimary()) {
            IntInsnNode(NEWARRAY, primaryTypeToInt(component))
        } else {
            TypeInsnNode(ANEWARRAY, component.getAsmDesc())
        }
        insnList.add(insn)
        inst.operands.forEach { stackPop() }
        stackPush(inst)
    }

    override fun visitMultiNewArrayInst(inst: MultiNewArrayInst) {
        val insn = MultiANewArrayInsnNode(inst.type.getAsmDesc(), inst.dims)
        insnList.add(insn)
        stackPush(inst)
    }

    override fun visitUnaryInst(inst: UnaryInst) {
        addOperandsToStack(inst.operands)
        val opcode = if (inst.opcode == UnaryOpcode.LENGTH) {
            ARRAYLENGTH
        } else {
            INEG + typeToInt(inst.getOperand().type)
        }
        val insn = InsnNode(opcode)
        insnList.add(insn)
        inst.operands.forEach { stackPop() }
        stackPush(inst)
    }

    override fun visitThrowInst(inst: ThrowInst) {
        addOperandsToStack(inst.operands)
        val insn = InsnNode(ATHROW)
        insnList.add(insn)
        inst.operands.forEach { stackPop() }
    }

    override fun visitSwitchInst(inst: SwitchInst) {
        addOperandsToStack(inst.operands)
        val default = getLabel(inst.default)
        val keys = inst.branches.keys.map { (it as IntConstant).value }.toIntArray()
        val labels = inst.branches.values.map { getLabel(it) }.toTypedArray()
        val insn = LookupSwitchInsnNode(default, keys, labels)
        insnList.add(insn)
        inst.operands.forEach { stackPop() }
    }

    override fun visitFieldLoadInst(inst: FieldLoadInst) {
        val field = inst.getField() as FieldValue
        val operands = if (!field.isStatic()) arrayOf(field.`object`!!) else arrayOf()
        addOperandsToStack(operands)
        val opcode = if (field.isStatic()) GETSTATIC else GETFIELD
        val insn = FieldInsnNode(opcode, field.field.`class`.getFullname(), field.field.name, field.type.getAsmDesc())
        insnList.add(insn)
        operands.forEach { stackPop() }
        stackPush(inst)
    }

    override fun visitFieldStoreInst(inst: FieldStoreInst) {
        val field = inst.getField() as FieldValue
        val operands = if (!field.isStatic()) arrayOf(field.`object`!!) else arrayOf()
        addOperandsToStack(operands)
        val opcode = if (field.isStatic()) PUTSTATIC else PUTFIELD
        val insn = FieldInsnNode(opcode, field.field.`class`.getFullname(), field.field.name, field.type.getAsmDesc())
        insnList.add(insn)
        stackPush(inst)
    }

    override fun visitInstanceOfInst(inst: InstanceOfInst) {
        addOperandsToStack(inst.operands)
        val insn = TypeInsnNode(INSTANCEOF, inst.targetType.getAsmDesc())
        insnList.add(insn)
        inst.operands.forEach { stackPop() }
        stackPush(inst)
    }

    override fun visitTableSwitchInst(inst: TableSwitchInst) {
        addOperandsToStack(arrayOf(inst.getIndex()))
        val min = (inst.getMin() as IntConstant).value
        val max = (inst.getMax() as IntConstant).value
        val default = getLabel(inst.default)
        val labels = inst.branches.map { getLabel(it) }.toTypedArray()
        val insn = TableSwitchInsnNode(min, max, default, *labels)
        insnList.add(insn)
        inst.operands.forEach { stackPop() }
    }

    override fun visitCallInst(inst: CallInst) {
        addOperandsToStack(inst.operands)
        val opcode = inst.opcode.toAsmOpcode()
        val insn = MethodInsnNode(opcode, inst.`class`.getFullname(), inst.method.name, inst.method.getAsmDesc(), opcode == INVOKEINTERFACE)
        insnList.add(insn)
        inst.operands.forEach { stackPop() }
        if (!inst.type.isVoid()) stackPush(inst)
    }

    override fun visitCmpInst(inst: CmpInst) {
        val isBranch = (inst.opcode in arrayOf(CmpOpcode.CMPG, CmpOpcode.CMPL))
                || (inst.opcode == CmpOpcode.EQ && inst.getLhv().type is LongType)
        if (isBranch) {
            val operands = mutableListOf(inst.getLhv())
            val opcode = if (inst.getLhv().type == VF.getZeroConstant(inst.getLhv().type)) {
                if (inst.getLhv().type is Reference) {
                    when (inst.opcode) {
                        CmpOpcode.EQ -> IFNULL
                        CmpOpcode.NE -> IFNONNULL
                        else -> throw UnexpectedOpcodeException("Branch cmp opcode ${inst.opcode.print()}")
                    }
                } else {
                    when (inst.opcode) {
                        CmpOpcode.EQ -> IFEQ
                        CmpOpcode.NE -> IFNE
                        CmpOpcode.LT -> IFLT
                        CmpOpcode.GT -> IFGT
                        CmpOpcode.LE -> IFLE
                        CmpOpcode.GE -> IFGE
                        else -> throw UnexpectedOpcodeException("Branch cmp opcode ${inst.opcode.print()}")
                    }
                }
            } else {
                operands.add(inst.getRhv())
                if (inst.getLhv().type is Reference) {
                    when (inst.opcode) {
                        CmpOpcode.EQ -> IF_ACMPEQ
                        CmpOpcode.NE -> IF_ACMPNE
                        else -> throw UnexpectedOpcodeException("Branch cmp opcode ${inst.opcode.print()}")
                    }
                } else {
                    when (inst.opcode) {
                        CmpOpcode.EQ -> IF_ICMPEQ
                        CmpOpcode.NE -> IF_ICMPNE
                        CmpOpcode.LT -> IF_ICMPLT
                        CmpOpcode.GT -> IF_ICMPGT
                        CmpOpcode.LE -> IF_ICMPLE
                        CmpOpcode.GE -> IF_ICMPGE
                        else -> throw UnexpectedOpcodeException("Branch cmp opcode ${inst.opcode.print()}")
                    }
                }
            }
            addOperandsToStack(operands.toTypedArray())
//            val opcode = when (inst.opcode) {
//                CmpOpcode.EQ -> {}
//                CmpOpcode.NE -> {}
//                CmpOpcode.LT -> {}
//                CmpOpcode.GT -> {}
//                CmpOpcode.LE -> {}
//                CmpOpcode.GE -> {}
//                else -> throw UnexpectedOpcodeException("Branch cmp opcode ${inst.opcode.print()}")
//            }
        } else {

        }
    }

    override fun visitCatchInst(inst: CatchInst) {}
    override fun visitPhiInst(inst: PhiInst) {}
}