package org.jetbrains.research.kfg.builder.asm

import com.abdullin.kthelper.assert.unreachable
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kfg.*
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.type.*
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.*
import java.util.*

val Type.fullInt
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
        else -> unreachable { log.error("Unexpected type for conversion: $name") }
    }

val Type.shortInt
    get() = when (this) {
        is LongType -> 1
        is Integral -> 0
        is FloatType -> 2
        is DoubleType -> 3
        is Reference -> 4
        else -> unreachable { log.error("Unexpected type for conversion: $name") }
    }

class AsmBuilder(override val cm: ClassManager, val method: Method) : MethodVisitor {
    private val bbInsns = hashMapOf<BasicBlock, MutableList<AbstractInsnNode>>()
    private val terminateInsns = hashMapOf<BasicBlock, MutableList<AbstractInsnNode>>()
    private val labels = method.basicBlocks.map { it to LabelNode() }.toMap()
    private val stack = ArrayDeque<Value>()
    private val locals = hashMapOf<Value, Int>()

    private var currentInsnList = mutableListOf<AbstractInsnNode>()
    private var maxLocals = 0
    private var maxStack = 0

    init {
        if (!method.isStatic) {
            val `this` = values.getThis(types.getRefType(method.`class`))
            locals[`this`] = getLocalFor(`this`)
        }
        for ((index, type) in method.argTypes.withIndex()) {
            val arg = values.getArgument(index, method, type)
            locals[arg] = getLocalFor(arg)
        }
    }

    private fun getInsnList(bb: BasicBlock) = bbInsns.getOrPut(bb, ::arrayListOf)
    private fun getTerminateInsnList(bb: BasicBlock) = terminateInsns.getOrPut(bb, ::arrayListOf)
    private fun stackPop() = stack.pop()
    private fun stackPop(amount: Int) = repeat(amount) { stackPop() }
    private fun stackPush(value: Value) {
        stack.push(value)
        if (stack.size > maxStack) maxStack = stack.size
    }

    private fun stackSave() {
        while (stack.isNotEmpty()) {
            val operand = stackPop()
            val local = getLocalFor(operand)
            val opcode = ISTORE + operand.type.shortInt
            val insn = VarInsnNode(opcode, local)
            currentInsnList.add(insn)
        }
    }

    private fun getLocalFor(value: Value) = locals.getOrPut(value) {
        val old = maxLocals
        maxLocals += when {
            value.type.isDWord -> 2
            else -> 1
        }
        old
    }

    private fun getLabel(bb: BasicBlock) = labels[bb]
            ?: throw UnknownInstance("No label corresponding to block ${bb.name}")

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
        is CharConstant -> LdcInsnNode(const.value.toInt())
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
        is NullConstant -> InsnNode(ACONST_NULL)
        is StringConstant -> LdcInsnNode(`const`.value)
        is ClassConstant -> LdcInsnNode(org.objectweb.asm.Type.getType(`const`.type.asmDesc))
        is MethodConstant -> unreachable { log.error("Cannot convert constant $`const`") }
    }

    // register all instructions for loading required arguments to stack
    private fun addOperandsToStack(operands: List<Value>) {
        stackSave()
        for (operand in operands) {
            val insn = when (operand) {
                is Constant -> convertConstantToInsn(operand)
                else -> {
                    val local = getLocalFor(operand)
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
        currentInsnList = getInsnList(bb)
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
        currentInsnList = getTerminateInsnList(inst.parent)
        val successor = getLabel(inst.successor)
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
        currentInsnList = getTerminateInsnList(inst.parent)
        addOperandsToStack(inst.operands)
        val opcode = if (inst.hasReturnValue) IRETURN + inst.returnType.shortInt else RETURN
        val insn = InsnNode(opcode)
        currentInsnList.add(insn)
    }

    override fun visitBranchInst(inst: BranchInst) {
        stackSave()
        currentInsnList = getTerminateInsnList(inst.parentUnsafe!!)

        val cond = inst.cond as? CmpInst ?: unreachable { log.error("Unknown branch condition: ${inst.print()}") }
        val opcode = if (cond.lhv.type is Reference) {
            when (cond.opcode) {
                is CmpOpcode.Eq -> IF_ACMPEQ
                is CmpOpcode.Neq -> IF_ACMPNE
                else -> throw InvalidOpcodeError("Branch cmp opcode ${cond.opcode}")
            }
        } else {
            when (cond.opcode) {
                is CmpOpcode.Eq -> IF_ICMPEQ
                is CmpOpcode.Neq -> IF_ICMPNE
                is CmpOpcode.Lt -> IF_ICMPLT
                is CmpOpcode.Gt -> IF_ICMPGT
                is CmpOpcode.Le -> IF_ICMPLE
                is CmpOpcode.Ge -> IF_ICMPGE
                else -> throw InvalidOpcodeError("Branch cmp opcode ${cond.opcode}")
            }
        }
        addOperandsToStack(cond.operands)
        val insn = JumpInsnNode(opcode, getLabel(inst.trueSuccessor))
        currentInsnList.add(insn)
        stackPop(inst.operands.size)

        val jump = JumpInsnNode(GOTO, getLabel(inst.falseSuccessor))
        currentInsnList.add(jump)
    }

    override fun visitCastInst(inst: CastInst) {
        val originalType = inst.operand.type
        val targetType = inst.type

        val insn = if (originalType.isPrimary && targetType.isPrimary) {
            val opcode = when (originalType) {
                is LongType -> when (targetType) {
                    is IntType -> L2I
                    is FloatType -> L2F
                    is DoubleType -> L2D
                    else -> throw InvalidOperandError("Invalid cast from ${originalType.name} to ${targetType.name}")
                }
                is Integral -> when (targetType) {
                    is LongType -> I2L
                    is FloatType -> I2F
                    is DoubleType -> I2D
                    is ByteType -> I2B
                    is CharType -> I2C
                    is ShortType -> I2S
                    is BoolType -> NOP
                    else -> throw InvalidOperandError("Invalid cast from ${originalType.name} to ${targetType.name}")
                }
                is FloatType -> when (targetType) {
                    is IntType -> F2I
                    is LongType -> F2L
                    is DoubleType -> F2D
                    else -> throw InvalidOperandError("Invalid cast from ${originalType.name} to ${targetType.name}")
                }
                is DoubleType -> when (targetType) {
                    is IntType -> D2I
                    is LongType -> D2L
                    is FloatType -> D2F
                    else -> throw InvalidOperandError("Invalid cast from ${originalType.name} to ${targetType.name}")
                }
                else -> throw InvalidOperandError("Invalid cast from ${originalType.name} to ${targetType.name}")
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
        currentInsnList = getTerminateInsnList(inst.parent)
        val operands = inst.operands
        addOperandsToStack(operands)
        val insn = InsnNode(ATHROW)
        currentInsnList.add(insn)
        stackPop(operands.size)
    }

    override fun visitSwitchInst(inst: SwitchInst) {
        stackSave()
        currentInsnList = getTerminateInsnList(inst.parent)
        addOperandsToStack(listOf(inst.key))
        val default = getLabel(inst.default)
        val branches = inst.branches
        val keys = branches.keys.map { (it as IntConstant).value }.sorted().toIntArray()
        val labels = keys.map { getLabel(branches[values.getIntConstant(it)]!!) }.toTypedArray()
        val insn = LookupSwitchInsnNode(default, keys, labels)
        currentInsnList.add(insn)
        stackPop()
    }

    override fun visitFieldLoadInst(inst: FieldLoadInst) {
        val opcode = if (inst.isStatic) GETSTATIC else GETFIELD
        val insn = FieldInsnNode(opcode, inst.field.`class`.fullname, inst.field.name, inst.type.asmDesc)
        val operands = inst.operands
        addOperandsToStack(operands)
        currentInsnList.add(insn)
        stackPop(operands.size)
        stackPush(inst)
    }

    override fun visitFieldStoreInst(inst: FieldStoreInst) {
        val opcode = if (inst.isStatic) PUTSTATIC else PUTFIELD
        val insn = FieldInsnNode(opcode, inst.field.`class`.fullname, inst.field.name, inst.type.asmDesc)
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
        currentInsnList = getTerminateInsnList(inst.parent)
        addOperandsToStack(listOf(inst.index))
        val min = (inst.min as IntConstant).value
        val max = (inst.max as IntConstant).value
        val default = getLabel(inst.default)
        val labels = inst.branches.map { getLabel(it) }.toTypedArray()
        val insn = TableSwitchInsnNode(min, max, default, *labels)
        currentInsnList.add(insn)
        stackPop()
    }

    override fun visitCallInst(inst: CallInst) {
        val opcode = inst.opcode.asmOpcode
        val insn = MethodInsnNode(opcode, inst.`class`.fullname, inst.method.name, inst.method.asmDesc, opcode == INVOKEINTERFACE)
        val operands = inst.operands
        addOperandsToStack(operands)
        currentInsnList.add(insn)
        stackPop(operands.size)
        if (!inst.type.isVoid) stackPush(inst)
    }

    override fun visitCmpInst(inst: CmpInst) {
        val isBranch = !(inst.opcode is CmpOpcode.Cmp || inst.opcode is CmpOpcode.Cmpg || inst.opcode is CmpOpcode.Cmpl)
        when {
            isBranch -> {
                // this kind of cmp insts are handled in visitBranch
                require(inst.users.size == 1) { "Unsupported usage of cmp inst" }
                require(inst.users.first() is BranchInst) { "Unsupported usage of cmp inst" }
            }
            else -> {
                val opcode = when (inst.opcode) {
                    is CmpOpcode.Cmp -> LCMP
                    is CmpOpcode.Cmpg -> when (inst.lhv.type) {
                        is FloatType -> FCMPG
                        is DoubleType -> DCMPG
                        else -> throw InvalidOperandError("Non-real operands of CMPG inst: ${inst.lhv.type}")
                    }
                    is CmpOpcode.Cmpl -> when (inst.lhv.type) {
                        is FloatType -> FCMPL
                        is DoubleType -> DCMPL
                        else -> throw InvalidOperandError("Non-real operands of CMPL inst: ${inst.lhv.type}")
                    }
                    else -> throw InvalidStateError("Unknown non-branch cmp inst ${inst.print()}")
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
        val local = getLocalFor(inst)
        val insn = VarInsnNode(ASTORE, local)
        currentInsnList.add(insn)
    }

    private fun buildPhiInst(inst: PhiInst) {
        val storeOpcode = ISTORE + inst.type.shortInt
        val local = getLocalFor(inst)
        for ((bb, value) in inst.incomings) {
            val bbInsns = getInsnList(bb)
            val loadIncoming = when (value) {
                is Constant -> convertConstantToInsn(value)
                else -> {
                    val lcl = getLocalFor(value)
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
        method.catchEntries.forEach {
            val `catch` = getLabel(it)
            val exception = it.exception.internalDesc
            for (thrower in it.throwers) {
                val from = getLabel(thrower)
                val to = getLabel(method.getNext(thrower))
                catchBlocks.add(TryCatchBlockNode(from, to, `catch`, exception))
            }
        }
        return catchBlocks
    }

    operator fun invoke(): MethodNode = build()

    override fun cleanup() {
        bbInsns.clear()
        terminateInsns.clear()
        stack.clear()
        locals.clear()

        currentInsnList.clear()
        maxLocals = 0
        maxStack = 0

        if (!method.isStatic) {
            val `this` = values.getThis(types.getRefType(method.`class`))
            locals[`this`] = getLocalFor(`this`)
        }
        for ((index, type) in method.argTypes.withIndex()) {
            val arg = values.getArgument(index, method, type)
            locals[arg] = getLocalFor(arg)
        }
    }

    fun build(): MethodNode {
        super.visit(method)
        method.flatten().filterIsInstance<PhiInst>().forEach { buildPhiInst(it) }
        val insnList = InsnList()
        for (bb in method.basicBlocks) {
            insnList.add(getLabel(bb))
            getInsnList(bb).forEach { insnList.add(it) }
            getTerminateInsnList(bb).forEach { insnList.add(it) }
        }
        method.mn.instructions = insnList
        method.mn.tryCatchBlocks = buildTryCatchBlocks()
        method.mn.maxLocals = maxLocals
        method.mn.maxStack = maxStack + 1
        // remove all info about local variables, because we don't kepp it updated
        method.mn.localVariables?.clear()
        return method.mn
    }
}