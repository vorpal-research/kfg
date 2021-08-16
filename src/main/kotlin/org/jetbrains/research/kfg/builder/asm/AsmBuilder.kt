package org.jetbrains.research.kfg.builder.asm

import org.jetbrains.research.kfg.*
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.type.*
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.assert.unreachable
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type.getType
import org.objectweb.asm.tree.*
import java.util.*

private val Type.fullInt
    get() = when (this) {
        is BoolType -> 5
        is ByteType -> 5
        is ShortType -> 7
        is CharType -> 6
        is IntType -> 0
        is LongType -> 1
        is FloatType -> 2
        is DoubleType -> 3
        is Reference -> 4
        else -> unreachable("Unexpected type for conversion: $name")
    }

private val Type.shortInt
    get() = when (this) {
        is LongType -> 1
        is Integral -> 0
        is FloatType -> 2
        is DoubleType -> 3
        is Reference -> 4
        else -> unreachable("Unexpected type for conversion: $name")
    }

class AsmBuilder(override val cm: ClassManager, val method: Method) : MethodVisitor {
    private val bbInsns = hashMapOf<BasicBlock, MutableList<AbstractInsnNode>>()
    private val terminateInsns = hashMapOf<BasicBlock, MutableList<AbstractInsnNode>>()
    private val labels = method.basicBlocks.associateWith { LabelNode() }
    private val stack = ArrayDeque<Value>()
    private val locals = hashMapOf<Value, Int>()

    private var currentInsnList = mutableListOf<AbstractInsnNode>()
    private var maxLocals = 0
    private var maxStack = 0

    init {
        if (!method.isStatic) {
            val instance = values.getThis(types.getRefType(method.klass))
            locals[instance] = instance.local
        }
        for ((index, type) in method.argTypes.withIndex()) {
            val arg = values.getArgument(index, method, type)
            locals[arg] = arg.local
        }
    }

    private val BasicBlock.insnList get() = bbInsns.getOrPut(this, ::arrayListOf)
    private val BasicBlock.terminateInsnList get() = terminateInsns.getOrPut(this, ::arrayListOf)

    private fun stackPop() = stack.pop()
    private fun stackPop(amount: Int) = repeat(amount) { stackPop() }
    private fun stackPush(value: Value) {
        stack.push(value)
        if (stack.size > maxStack) maxStack = stack.size
    }

    private fun stackSave() {
        while (stack.isNotEmpty()) {
            val operand = stackPop()
            val local = operand.local
            val opcode = ISTORE + operand.type.shortInt
            val insn = VarInsnNode(opcode, local)
            currentInsnList.add(insn)
        }
    }

    private val Value.local
        get() = locals.getOrPut(this) {
            val old = maxLocals
            maxLocals += when {
                type.isDWord -> 2
                else -> 1
            }
            old
        }

    private val BasicBlock.label
        get() = labels[this]
            ?: throw UnknownInstanceException("No label corresponding to block $name")

    private fun convertConstantToInsn(constant: Constant) = when (constant) {
        is BoolConstant -> InsnNode(if (constant.value) ICONST_1 else ICONST_0)
        is ByteConstant -> IntInsnNode(BIPUSH, constant.value.toInt())
        is ShortConstant -> IntInsnNode(SIPUSH, constant.value.toInt())
        is IntConstant -> when (constant.value) {
            in -1..5 -> InsnNode(ICONST_0 + constant.value)
            in Byte.MIN_VALUE..Byte.MAX_VALUE -> IntInsnNode(BIPUSH, constant.value)
            in Short.MIN_VALUE..Short.MAX_VALUE -> IntInsnNode(SIPUSH, constant.value)
            else -> LdcInsnNode(constant.value)
        }
        is CharConstant -> LdcInsnNode(constant.value.code)
        is LongConstant -> when (constant.value) {
            in 0..1 -> InsnNode(LCONST_0 + constant.value.toInt())
            else -> LdcInsnNode(constant.value)
        }
        is FloatConstant -> when (constant.value) {
            0.0F -> InsnNode(FCONST_0)
            1.0F -> InsnNode(FCONST_1)
            2.0F -> InsnNode(FCONST_2)
            else -> LdcInsnNode(constant.value)
        }
        is DoubleConstant -> when (constant.value) {
            0.0 -> InsnNode(DCONST_0)
            1.0 -> InsnNode(DCONST_1)
            else -> LdcInsnNode(constant.value)
        }
        is NullConstant -> InsnNode(ACONST_NULL)
        is StringConstant -> LdcInsnNode(constant.value)
        is ClassConstant -> LdcInsnNode(getType(constant.type.asmDesc))
        is MethodConstant -> unreachable("Cannot convert constant $constant")
    }

    // register all instructions for loading required arguments to stack
    private fun addOperandsToStack(operands: List<Value>) {
        stackSave()
        for (operand in operands) {
            val insn = when (operand) {
                is Constant -> convertConstantToInsn(operand)
                else -> {
                    val local = operand.local
                    val opcode = ILOAD + operand.type.shortInt
                    VarInsnNode(opcode, local)
                }
            }
            currentInsnList.add(insn)
            stackPush(operand)
        }
    }

    override fun visitBasicBlock(bb: BasicBlock) {
        stack.clear()
        currentInsnList = bb.insnList
        super.visitBasicBlock(bb)
    }

    override fun visitArrayLoadInst(inst: ArrayLoadInst) {
        val opcode = IALOAD + inst.type.fullInt
        val insn = InsnNode(opcode)
        val operands = inst.operands
        addOperandsToStack(operands)
        currentInsnList.add(insn)
        stackPop(operands.size)
        stackPush(inst)
    }

    override fun visitArrayStoreInst(inst: ArrayStoreInst) {
        val type = inst.arrayComponent
        val opcode = IASTORE + type.fullInt
        val insn = InsnNode(opcode)
        val operands = inst.operands
        addOperandsToStack(operands)
        currentInsnList.add(insn)
        stackPop(operands.size)
    }

    override fun visitBinaryInst(inst: BinaryInst) {
        val opcode = inst.opcode.asmOpcode + inst.type.shortInt
        val insn = InsnNode(opcode)
        val operands = inst.operands
        addOperandsToStack(operands)
        currentInsnList.add(insn)
        stackPop(operands.size)
        stackPush(inst)
    }

    override fun visitJumpInst(inst: JumpInst) {
        stackSave()
        currentInsnList = inst.parent.terminateInsnList
        val successor = inst.successor.label
        val insn = JumpInsnNode(GOTO, successor)
        currentInsnList.add(insn)
    }

    override fun visitNewInst(inst: NewInst) {
        val insn = TypeInsnNode(NEW, inst.type.internalDesc)
        currentInsnList.add(insn)
        stackPush(inst)
    }

    override fun visitReturnInst(inst: ReturnInst) {
        stackSave()
        currentInsnList = inst.parent.terminateInsnList
        addOperandsToStack(inst.operands)
        val opcode = if (inst.hasReturnValue) IRETURN + inst.returnType.shortInt else RETURN
        val insn = InsnNode(opcode)
        currentInsnList.add(insn)
    }

    override fun visitBranchInst(inst: BranchInst) {
        stackSave()
        currentInsnList = inst.parent.terminateInsnList

        val cond = inst.cond as? CmpInst
            ?: unreachable("Unknown branch condition: ${inst.print()}")
        val opcode = if (cond.lhv.type is Reference) {
            when (cond.opcode) {
                CmpOpcode.EQ -> IF_ACMPEQ
                CmpOpcode.NEQ -> IF_ACMPNE
                else -> throw InvalidOpcodeException("Branch cmp opcode ${cond.opcode}")
            }
        } else {
            when (cond.opcode) {
                CmpOpcode.EQ -> IF_ICMPEQ
                CmpOpcode.NEQ -> IF_ICMPNE
                CmpOpcode.LT -> IF_ICMPLT
                CmpOpcode.GT -> IF_ICMPGT
                CmpOpcode.LE -> IF_ICMPLE
                CmpOpcode.GE -> IF_ICMPGE
                else -> throw InvalidOpcodeException("Branch cmp opcode ${cond.opcode}")
            }
        }
        addOperandsToStack(cond.operands)
        val insn = JumpInsnNode(opcode, inst.trueSuccessor.label)
        currentInsnList.add(insn)
        stackPop(inst.operands.size)

        val jump = JumpInsnNode(GOTO, inst.falseSuccessor.label)
        currentInsnList.add(jump)
    }

    override fun visitCastInst(inst: CastInst) {
        val originalType = inst.operand.type
        val targetType = inst.type

        val throwEx = {
            throw InvalidOperandException("Invalid cast from ${originalType.name} to ${targetType.name}")
        }
        val insn = if (originalType.isPrimary && targetType.isPrimary) {
            val opcode = when (originalType) {
                is LongType -> when (targetType) {
                    is IntType -> L2I
                    is FloatType -> L2F
                    is DoubleType -> L2D
                    else -> throwEx()
                }
                is Integral -> when (targetType) {
                    is LongType -> I2L
                    is FloatType -> I2F
                    is DoubleType -> I2D
                    is ByteType -> I2B
                    is CharType -> I2C
                    is ShortType -> I2S
                    is BoolType -> NOP
                    else -> throwEx()
                }
                is FloatType -> when (targetType) {
                    is IntType -> F2I
                    is LongType -> F2L
                    is DoubleType -> F2D
                    else -> throwEx()
                }
                is DoubleType -> when (targetType) {
                    is IntType -> D2I
                    is LongType -> D2L
                    is FloatType -> D2F
                    else -> throwEx()
                }
                else -> throwEx()
            }
            InsnNode(opcode)
        } else {
            TypeInsnNode(CHECKCAST, targetType.internalDesc)
        }

        val operands = inst.operands
        addOperandsToStack(operands)
        currentInsnList.add(insn)
        stackPop(operands.size)
        stackPush(inst)
    }

    override fun visitEnterMonitorInst(inst: EnterMonitorInst) {
        val insn = InsnNode(MONITORENTER)
        val operands = inst.operands
        addOperandsToStack(operands)
        currentInsnList.add(insn)
        stackPop(operands.size)
    }

    override fun visitExitMonitorInst(inst: ExitMonitorInst) {
        val insn = InsnNode(MONITOREXIT)
        val operands = inst.operands
        addOperandsToStack(operands)
        currentInsnList.add(insn)
        stackPop(operands.size)
    }

    override fun visitNewArrayInst(inst: NewArrayInst) {
        val component = inst.component
        val insn = when {
            inst.numDimensions > 1 -> MultiANewArrayInsnNode(inst.type.asmDesc, inst.numDimensions)
            component.isPrimary -> IntInsnNode(NEWARRAY, primaryTypeToInt(component))
            else -> TypeInsnNode(ANEWARRAY, component.internalDesc)
        }
        val operands = inst.operands
        addOperandsToStack(operands)
        currentInsnList.add(insn)
        stackPop(operands.size)
        stackPush(inst)
    }

    override fun visitUnaryInst(inst: UnaryInst) {
        val opcode = when (inst.opcode) {
            UnaryOpcode.LENGTH -> ARRAYLENGTH
            else -> INEG + inst.operand.type.shortInt
        }
        val insn = InsnNode(opcode)
        val operands = inst.operands
        addOperandsToStack(operands)
        currentInsnList.add(insn)
        stackPop(operands.size)
        stackPush(inst)
    }

    override fun visitThrowInst(inst: ThrowInst) {
        stackSave()
        currentInsnList = inst.parent.terminateInsnList
        val operands = inst.operands
        addOperandsToStack(operands)
        val insn = InsnNode(ATHROW)
        currentInsnList.add(insn)
        stackPop(operands.size)
    }

    override fun visitSwitchInst(inst: SwitchInst) {
        stackSave()
        currentInsnList = inst.parent.terminateInsnList
        addOperandsToStack(listOf(inst.key))
        val default = inst.default.label
        val branches = inst.branches
        val keys = branches.keys.map { (it as IntConstant).value }.sorted().toIntArray()
        val labels = keys.map { branches[values.getInt(it)]!!.label }.toTypedArray()
        val insn = LookupSwitchInsnNode(default, keys, labels)
        currentInsnList.add(insn)
        stackPop()
    }

    override fun visitFieldLoadInst(inst: FieldLoadInst) {
        val opcode = if (inst.isStatic) GETSTATIC else GETFIELD
        val insn = FieldInsnNode(opcode, inst.field.klass.fullName, inst.field.name, inst.type.asmDesc)
        val operands = inst.operands
        addOperandsToStack(operands)
        currentInsnList.add(insn)
        stackPop(operands.size)
        stackPush(inst)
    }

    override fun visitFieldStoreInst(inst: FieldStoreInst) {
        val opcode = if (inst.isStatic) PUTSTATIC else PUTFIELD
        val insn = FieldInsnNode(opcode, inst.field.klass.fullName, inst.field.name, inst.type.asmDesc)
        val operands = inst.operands
        addOperandsToStack(operands)
        currentInsnList.add(insn)
        stackPop(operands.size)
    }

    override fun visitInstanceOfInst(inst: InstanceOfInst) {
        val insn = TypeInsnNode(INSTANCEOF, inst.targetType.internalDesc)
        val operands = inst.operands
        addOperandsToStack(operands)
        currentInsnList.add(insn)
        stackPop(operands.size)
        stackPush(inst)
    }

    override fun visitTableSwitchInst(inst: TableSwitchInst) {
        stackSave()
        currentInsnList = inst.parent.terminateInsnList
        addOperandsToStack(listOf(inst.index))
        val min = (inst.min as IntConstant).value
        val max = (inst.max as IntConstant).value
        val default = inst.default.label
        val labels = inst.branches.map { it.label }.toTypedArray()
        val insn = TableSwitchInsnNode(min, max, default, *labels)
        currentInsnList.add(insn)
        stackPop()
    }

    override fun visitCallInst(inst: CallInst) {
        val opcode = inst.opcode.asmOpcode
        val insn = MethodInsnNode(
            opcode,
            inst.klass.fullName,
            inst.method.name,
            inst.method.asmDesc,
            opcode == INVOKEINTERFACE
        )
        val operands = inst.operands
        addOperandsToStack(operands)
        currentInsnList.add(insn)
        stackPop(operands.size)
        if (!inst.type.isVoid) stackPush(inst)
    }

    override fun visitCmpInst(inst: CmpInst) {
        val isBranch = !(inst.opcode == CmpOpcode.CMP || inst.opcode == CmpOpcode.CMPG || inst.opcode == CmpOpcode.CMPL)
        when {
            isBranch -> {
                ktassert(inst.users.any { it is BranchInst }, "Unsupported usage of cmp inst")
            }
            else -> {
                val opcode = when (inst.opcode) {
                    CmpOpcode.CMP -> LCMP
                    CmpOpcode.CMPG -> when (inst.lhv.type) {
                        is FloatType -> FCMPG
                        is DoubleType -> DCMPG
                        else -> throw InvalidOperandException("Non-real operands of CMPG inst: ${inst.lhv.type}")
                    }
                    CmpOpcode.CMPL -> when (inst.lhv.type) {
                        is FloatType -> FCMPL
                        is DoubleType -> DCMPL
                        else -> throw InvalidOperandException("Non-real operands of CMPL inst: ${inst.lhv.type}")
                    }
                    else -> throw InvalidStateException("Unknown non-branch cmp inst ${inst.print()}")
                }
                val insn = InsnNode(opcode)
                val operands = inst.operands
                addOperandsToStack(operands)
                currentInsnList.add(insn)
                stackPop(operands.size)
                stackPush(inst)
            }
        }
    }

    override fun visitCatchInst(inst: CatchInst) {
        val local = inst.local
        val insn = VarInsnNode(ASTORE, local)
        currentInsnList.add(insn)
    }

    private fun buildPhiInst(inst: PhiInst) {
        val storeOpcode = ISTORE + inst.type.shortInt
        val local = inst.local
        for ((bb, value) in inst.incomings) {
            val bbInsns = bb.insnList
            val loadIncoming = when (value) {
                is Constant -> convertConstantToInsn(value)
                else -> {
                    val lcl = value.local
                    val opcode = ILOAD + value.type.shortInt
                    VarInsnNode(opcode, lcl)
                }
            }
            bbInsns.add(loadIncoming)
            val insn = VarInsnNode(storeOpcode, local)
            bbInsns.add(insn)
        }
    }

    private fun buildTryCatchBlocks(): List<TryCatchBlockNode> {
        val catchBlocks = mutableListOf<TryCatchBlockNode>()
        for (catchBlock in method.catchEntries) {
            val `catch` = catchBlock.label
            val exception = catchBlock.exception.internalDesc
            for (thrower in catchBlock.throwers) {
                val from = thrower.label
                val to = method.getNext(thrower).label
                catchBlocks.add(TryCatchBlockNode(from, to, `catch`, exception))
            }
        }
        return catchBlocks
    }

    operator fun invoke(): MethodNode = build()

    override fun cleanup() {
        bbInsns.forEach { it.value.clear() }
        bbInsns.clear()
        terminateInsns.forEach { it.value.clear() }
        terminateInsns.clear()
        stack.clear()
        locals.clear()

        currentInsnList.clear()
        maxLocals = 0
        maxStack = 0

        if (!method.isStatic) {
            val instance = values.getThis(types.getRefType(method.klass))
            locals[instance] = instance.local
        }
        for ((index, type) in method.argTypes.withIndex()) {
            val arg = values.getArgument(index, method, type)
            locals[arg] = arg.local
        }
    }

    fun build(): MethodNode {
        super.visit(method)
        method.flatten().filterIsInstance<PhiInst>().forEach { buildPhiInst(it) }
        val insnList = InsnList()
        for (bb in method.basicBlocks) {
            insnList.add(bb.label)
            bb.insnList.forEach { insnList.add(it) }
            bb.terminateInsnList.forEach { insnList.add(it) }
        }
        method.mn.instructions = insnList
        method.mn.tryCatchBlocks = buildTryCatchBlocks()
        method.mn.maxLocals = maxLocals
        method.mn.maxStack = maxStack + 1
        // remove all info about local variables, because we don't keep it updated
        method.mn.localVariables?.clear()
        cleanup()
        return method.mn
    }
}