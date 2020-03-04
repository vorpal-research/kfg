package org.jetbrains.research.kfg.builder.cfg

import com.abdullin.kthelper.`try`
import com.abdullin.kthelper.assert.unreachable
import com.abdullin.kthelper.collection.queueOf
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.InvalidOpcodeError
import org.jetbrains.research.kfg.InvalidOperandError
import org.jetbrains.research.kfg.UnsupportedOperation
import org.jetbrains.research.kfg.analysis.IRVerifier
import org.jetbrains.research.kfg.analysis.NullTypeAdapter
import org.jetbrains.research.kfg.builder.cfg.impl.FrameStack
import org.jetbrains.research.kfg.builder.cfg.impl.FrameState
import org.jetbrains.research.kfg.builder.cfg.impl.LocalArray
import org.jetbrains.research.kfg.ir.*
import org.jetbrains.research.kfg.ir.value.Slot
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.type.*
import org.jetbrains.research.kfg.util.print
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.*
import java.util.*

fun MethodNode.instructions(): List<AbstractInsnNode> {
    val list = mutableListOf<AbstractInsnNode>()
    var current: AbstractInsnNode? = instructions.first
    while (current != null) {
        list += current
        current = current.next
    }
    return list
}

fun MethodNode.tryCatchBlocks() = this.tryCatchBlocks.mapNotNull { it as? TryCatchBlockNode }

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

class CfgBuilder(val cm: ClassManager, val method: Method) : Opcodes {
    val instructions get() = cm.instruction
    val values get() = cm.value
    val types get() = cm.type

    private data class BlockFrame(val bb: BasicBlock) {
        val locals = LocalArray()
        val stack = FrameStack()

        val stackPhis = arrayListOf<PhiInst>()
        val localPhis = hashMapOf<Int, PhiInst>()
    }

    private val visitedBlocks = mutableSetOf<BasicBlock>()
    private val locals = LocalArray()
    private val nodeToBlock = hashMapOf<AbstractInsnNode, BasicBlock>()
    private val blockToNode = hashMapOf<BasicBlock, MutableList<AbstractInsnNode>>()
    private val frames = hashMapOf<BasicBlock, BlockFrame>()
    private val stack = ArrayList<Value>()
    private var currentLocation = Location()
    private lateinit var lastFrame: FrameState

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
    }

    private fun createStackPhis(bb: BasicBlock, predFrames: List<BlockFrame>, stackState: SortedMap<Int, Type>) {
        val predStacks = predFrames.map { it.bb to it.stack.takeLast(stackState.size) }.toMap()
        for ((index, type) in stackState) {
            val incomings = predStacks.map { it.key to it.value[index] }.toMap()
            val incomingValues = incomings.values.toSet()
            when {
                incomingValues.size > 1 -> {
                    val newPhi = instructions.getPhi(type, incomings)
                    addInstruction(bb, newPhi)
                    push(newPhi)
                }
                else -> push(incomingValues.first())
            }
        }
    }

    private fun createStackCyclePhis(bb: BasicBlock, stackState: SortedMap<Int, Type>) {
        val sf = frames.getValue(bb)
        for ((_, type) in stackState) {
            val phi = instructions.getPhi(type, mapOf())
            addInstruction(bb, phi)
            push(phi)
            sf.stackPhis.add(phi as PhiInst)
        }
    }

    private fun createLocalPhis(bb: BasicBlock, predFrames: List<BlockFrame>, definedLocals: Map<Int, Type>) {
        for ((local, type) in definedLocals) {
            val incomings = predFrames.map {
                it.bb to (it.locals[local]
                        ?: unreachable { log.error("Predecessor frame does not contain a local value $local") })
            }.toMap()

            val incomingValues = incomings.values.toSet()
            when {
                incomingValues.size > 1 -> {
                    val newPhi = instructions.getPhi(type, incomings)
                    addInstruction(bb, newPhi)
                    locals[local] = newPhi
                }
                else -> locals[local] = incomingValues.first()
            }
        }
    }

    private fun createLocalCyclePhis(bb: BasicBlock, definedLocals: Map<Int, Type>) {
        val sf = frames.getValue(bb)
        for ((index, type) in definedLocals) {
            val phi = instructions.getPhi(type, mapOf())
            addInstruction(bb, phi)
            locals[index] = phi
            sf.localPhis[index] = phi as PhiInst
        }
    }

    private fun convertConst(insn: InsnNode) = push(
            when (val opcode = insn.opcode) {
                ACONST_NULL -> values.getNullConstant()
                ICONST_M1 -> values.getIntConstant(-1)
                in ICONST_0..ICONST_5 -> values.getIntConstant(opcode - ICONST_0)
                in LCONST_0..LCONST_1 -> values.getLongConstant((opcode - LCONST_0).toLong())
                in FCONST_0..FCONST_2 -> values.getFloatConstant((opcode - FCONST_0).toFloat())
                in DCONST_0..DCONST_1 -> values.getDoubleConstant((opcode - DCONST_0).toDouble())
                else -> throw InvalidOpcodeError("Unknown const $opcode")
            }
    )

    private fun convertArrayLoad(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val index = pop()
        val arrayRef = pop()
        val inst = instructions.getArrayLoad(arrayRef, index)
        addInstruction(bb, inst)
        push(inst)
    }

    private fun convertArrayStore(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val value = pop()
        val index = pop()
        val array = pop()
        addInstruction(bb, instructions.getArrayStore(array, index, value))
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
        val inst = instructions.getBinary(binOp, lhv, rhv)
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
        val inst = instructions.getUnary(op, operand)
        addInstruction(bb, inst)
        push(inst)
    }

    private fun convertCast(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val op = pop()
        val type = when (insn.opcode) {
            I2L, F2L, D2L -> types.longType
            I2F, L2F, D2F -> types.floatType
            I2D, L2D, F2D -> types.doubleType
            L2I, F2I, D2I -> types.intType
            I2B -> types.byteType
            I2C -> types.charType
            I2S -> types.shortType
            else -> throw InvalidOpcodeError("Cast opcode ${insn.opcode}")
        }
        val inst = instructions.getCast(type, op)
        addInstruction(bb, inst)
        push(inst)
    }

    private fun convertCmp(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val lhv = pop()
        val rhv = pop()
        val op = toCmpOpcode(insn.opcode)
        val resType = getCmpResultType(types, op)
        val inst = instructions.getCmp(resType, op, lhv, rhv)
        addInstruction(bb, inst)
        push(inst)
    }

    private fun convertReturn(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        when (RETURN) {
            insn.opcode -> addInstruction(bb, instructions.getReturn())
            else -> addInstruction(bb, instructions.getReturn(pop()))
        }
    }

    private fun convertMonitor(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val owner = pop()
        when (insn.opcode) {
            MONITORENTER -> addInstruction(bb, instructions.getEnterMonitor(owner))
            MONITOREXIT -> addInstruction(bb, instructions.getExitMonitor(owner))
            else -> throw InvalidOpcodeError("Monitor opcode ${insn.opcode}")
        }
    }

    private fun convertThrow(insn: InsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val throwable = pop()
        addInstruction(bb, instructions.getThrow(throwable))
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
                val inst = instructions.getNewArray(type, count)
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
        val type = `try` { parseDesc(types, insn.desc) }.getOrElse { types.getRefType(insn.desc) }
        when (opcode) {
            NEW -> {
                val inst = instructions.getNew(type)
                addInstruction(bb, inst)
                push(inst)
            }
            ANEWARRAY -> {
                val count = pop()
                val inst = instructions.getNewArray(type, count)
                addInstruction(bb, inst)
                push(inst)
            }
            CHECKCAST -> {
                val castable = pop()
                val inst = instructions.getCast(type, castable)
                addInstruction(bb, inst)
                push(inst)
            }
            INSTANCEOF -> {
                val obj = pop()
                val inst = instructions.getInstanceOf(type, obj)
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
        val `class` = cm[insn.owner]
        when (opcode) {
            GETSTATIC -> {
                val field = `class`.getField(insn.name, fieldType)
                val inst = instructions.getFieldLoad(field)
                addInstruction(bb, inst)
                push(inst)
            }
            PUTSTATIC -> {
                val field = `class`.getField(insn.name, fieldType)
                val value = pop()
                addInstruction(bb, instructions.getFieldStore(field, value))
            }
            GETFIELD -> {
                val field = `class`.getField(insn.name, fieldType)
                val owner = pop()
                val inst = instructions.getFieldLoad(owner, field)
                addInstruction(bb, inst)
                push(inst)
            }
            PUTFIELD -> {
                val value = pop()
                val owner = pop()
                val field = `class`.getField(insn.name, fieldType)
                addInstruction(bb, instructions.getFieldStore(owner, field, value))
            }
        }
    }

    private fun convertMethodInsn(insn: MethodInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val `class` = when {
            // FIXME: This is literally fucked up. If insn owner is an array, class of the CallInst should be java/lang/Object,
            //  because java array don't have their own methods
            insn.owner.startsWith("[") -> cm[TypeFactory.objectClass]
            else -> cm[insn.owner]
        }
        val method = `class`.getMethod(insn.name, insn.desc)
        val args = arrayListOf<Value>()
        method.argTypes.forEach { _ -> args.add(0, pop()) }

        val isNamed = !method.returnType.isVoid
        val opcode = toCallOpcode(insn.opcode)
        val call = when (insn.opcode) {
            INVOKESTATIC -> instructions.getCall(opcode, method, `class`, args.toTypedArray(), isNamed)
            in arrayOf(INVOKEVIRTUAL, INVOKESPECIAL, INVOKEINTERFACE) -> {
                val obj = pop()
                instructions.getCall(opcode, method, `class`, obj, args.toTypedArray(), isNamed)
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

        when (insn.opcode) {
            GOTO -> {
                val trueSuccessor = nodeToBlock.getValue(insn.label)
                addInstruction(bb, instructions.getJump(trueSuccessor))
            }
            else -> {
                val falseSuccessor = nodeToBlock.getValue(insn.next)
                val trueSuccessor = nodeToBlock.getValue(insn.label)
                val name = Slot()
                val rhv = pop()
                val opc = toCmpOpcode(insn.opcode)
                val resType = getCmpResultType(types, opc)
                val cond = when (insn.opcode) {
                    in IFEQ..IFLE -> instructions.getCmp(name, resType, opc, rhv, values.getZeroConstant(rhv.type))
                    in IF_ICMPEQ..IF_ACMPNE -> instructions.getCmp(name, resType, opc, pop(), rhv)
                    in IFNULL..IFNONNULL -> instructions.getCmp(name, resType, opc, rhv, values.getNullConstant())
                    else -> throw InvalidOpcodeError("Jump opcode ${insn.opcode}")
                }
                addInstruction(bb, cond)
                val castedCond = if (cond.type is BoolType) cond else instructions.getCast(types.boolType, cond)
                addInstruction(bb, instructions.getBranch(castedCond, trueSuccessor, falseSuccessor))
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
                    else -> cm["$temp"]
                }
                push(values.getClassConstant(klass))
            }
            is org.objectweb.asm.Handle -> {
                val `class` = cm[cst.owner]
                val method = `class`.getMethod(cst.name, cst.desc)
                push(values.getMethodConstant(method))
            }
            else -> throw InvalidOperandError("Unknown object $cst")
        }
    }

    private fun convertIincInsn(insn: IincInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val lhv = locals[insn.`var`] ?: throw InvalidOperandError("${insn.`var`} local is invalid")
        val rhv = instructions.getBinary(BinaryOpcode.Add(), values.getIntConstant(insn.incr), lhv)
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
        addInstruction(bb, instructions.getTableSwitch(index, min, max, default, branches))
    }

    private fun convertLookupSwitchInsn(insn: LookupSwitchInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val default = nodeToBlock.getValue(insn.dflt)
        val branches = hashMapOf<Value, BasicBlock>()
        val key = pop()
        for (i in 0..insn.keys.lastIndex) {
            branches[values.getIntConstant(insn.keys[i] as Int)] = nodeToBlock.getValue(insn.labels[i] as LabelNode)
        }
        addInstruction(bb, instructions.getSwitch(key, default, branches))
    }

    private fun convertMultiANewArrayInsn(insn: MultiANewArrayInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val dimensions = arrayListOf<Value>()
        for (it in 0 until insn.dims) dimensions.add(pop())
        val type = parseDesc(types, insn.desc)
        val inst = instructions.getNewArray(type, dimensions.toTypedArray())
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
    private fun convertFrame(insn: FrameNode) {
        val block = nodeToBlock.getValue(insn)
        val predecessors = when (block) {
            is CatchBlock -> block.allPredecessors
            else -> block.predecessors
        }
        val isCycle = predecessors.any { it !in visitedBlocks }
        val predFrames = predecessors.map { frames.getValue(it) }

        fun FrameState.copyStackAndLocals(predFrames: List<BlockFrame>) {
            when {
                predFrames.size == 1 -> {
                    val predFrame = predFrames.first()
                    this.stack.keys.forEach {
                        push(predFrame.stack[it])
                    }
                    this.local.keys.forEach { i ->
                        locals[i] = predFrame.locals.getValue(i)
                    }
                }
                !isCycle -> {
                    createStackPhis(block, predFrames, this.stack)
                    createLocalPhis(block, predFrames, this.local)
                }
                else -> {
                    createStackCyclePhis(block, this.stack)
                    createLocalCyclePhis(block, this.local)
                }
            }
        }

        fun FrameState.copyLocals(predFrames: List<BlockFrame>) {
            when {
                predFrames.size == 1 -> {
                    val predFrame = predFrames.first()
                    this.local.filterValues { it !is VoidType }.keys.forEach { i ->
                        locals[i] = predFrame.locals.getValue(i)
                    }
                }
                !isCycle -> createLocalPhis(block, predFrames, this.local)
                else -> createLocalCyclePhis(block, this.local)
            }
        }

        stack.clear()
        locals.clear()

        lastFrame = when (insn.type) {
            F_NEW -> unreachable { log.error("Unknown frame node type F_NEW") }
            F_FULL -> FrameState.parse(types, method, insn)
            F_APPEND -> lastFrame.appendFrame(insn)
            F_CHOP -> lastFrame.dropFrame(insn)
            F_SAME -> lastFrame.copy()
            F_SAME1 -> lastFrame.copy1(insn)
            else -> unreachable { log.error("Unknown frame node type $insn") }
        }

        when (block) {
            is CatchBlock -> {
                lastFrame.copyLocals(predFrames)

                val inst = instructions.getCatch(block.exception)
                addInstruction(block, inst)
                push(inst)
            }
            else -> {
                lastFrame.copyStackAndLocals(predFrames)
            }
        }
    }

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
                        addInstruction(entry, instructions.getJump(bb))
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

            if (throwers.isEmpty()) {
                throwers.add(thrower)
                thrower.addHandler(handle)
            }
            handle.addThrowers(throwers)
            method.addCatchBlock(handle)
        }
    }

    private fun buildFrames() {
        if (method.isEmpty()) return
        val sf = frames.getOrPut(method.entry) { BlockFrame(method.entry) }
        sf.locals.putAll(locals)

        for (bb in method.basicBlocks.drop(1)) {
            frames.getOrPut(bb) { BlockFrame(bb) }
        }
    }

    private fun buildCyclePhis() {
        for (block in method) {
            val sf = frames.getValue(block)
            val predFrames = when (block) {
                is CatchBlock -> block.allPredecessors.map { frames.getValue(it) }
                else -> block.predecessors.map { frames.getValue(it) }
            }

            for ((index, phi) in sf.stackPhis.withIndex()) {
                val incomings = predFrames.map { it.bb to it.stack[index] }.toMap()

                val newPhi = instructions.getPhi(phi.name, phi.type, incomings)
                phi.replaceAllUsesWith(newPhi)
                phi.operands.forEach { it.removeUser(phi) }
                block.replace(phi, newPhi)
            }

            for ((local, phi) in sf.localPhis) {
                val incomings = predFrames.map {
                    it.bb to (it.locals[local]
                            ?: unreachable { log.error("Predecessor frame does not contain a local value $local") })
                }.toMap()

                val newPhi = instructions.getPhi(phi.type, incomings)
                phi.replaceAllUsesWith(newPhi)
                phi.operands.forEach { it.removeUser(phi) }
                block.replace(phi, newPhi)
            }
        }
    }

    private fun optimizePhis() {
        val queue = queueOf(method.flatten().filterIsInstance<PhiInst>())
        loop@ while (queue.isNotEmpty()) {
            val top = queue.poll()
            val incomings = top.incomingValues
            val incomingsSet = incomings.toSet()
            val instUsers = top.users.mapNotNull { it as? Instruction }
            val operands = top.operands

            when {
                incomingsSet.size == 1 -> {
                    val first = incomingsSet.first()
                    top.replaceAllUsesWith(first)
                    operands.forEach { it.removeUser(top) }
                    if (first is PhiInst) queue.add(first)
                    top.parentUnsafe?.remove(top) ?: continue@loop
                    operands.mapNotNull { it as? PhiInst }.forEach { queue.add(it) }
                }
                incomingsSet.size == 2 && top in incomingsSet -> {
                    top.replaceAllUsesWith(when (top) {
                        incomingsSet.first() -> incomingsSet.last()
                        else -> incomingsSet.first()
                    })
                    operands.forEach { it.removeUser(top) }
                    instUsers.mapNotNull { it as? PhiInst }.forEach { queue.add(it) }
                }
                instUsers.isEmpty() -> {
                    operands.forEach { it.removeUser(top) }
                    top.parentUnsafe?.remove(top) ?: continue@loop
                    operands.mapNotNull { it as? PhiInst }.forEach { queue.add(it) }
                }
                instUsers.size == 1 && instUsers.first() == top -> {
                    operands.forEach { it.removeUser(top) }
                    top.parentUnsafe?.remove(top)
                    operands.mapNotNull { it as? PhiInst }
                            .mapNotNull { if (it == top) null else it }.toList()
                            .forEach { queue.add(it) }
                }
            }
        }
    }

    private fun finishState(block: BasicBlock) {
        if (block in visitedBlocks) return
        if (block !in method) return

        val last = block.instructions.lastOrNull()
        if (last == null || !last.isTerminate) {
            when (block.successors.size) {
                1 -> addInstruction(block, instructions.getJump(block.successors.first()))
                0 -> {}
                else -> unreachable { log.error("Unexpected block in finish") }
            }
        }
        reserveState(block)
        visitedBlocks += block
    }

    private fun initFrame() {
        var localIndex = 0
        if (!method.isStatic) {
            val `this` = values.getThis(types.getRefType(method.`class`))
            locals[localIndex++] = `this`
            method.slottracker.addValue(`this`)
        }
        for ((index, type) in method.argTypes.withIndex()) {
            val arg = values.getArgument(index, method, type)
            locals[localIndex] = arg
            if (type.isDWord) localIndex += 2
            else ++localIndex
            method.slottracker.addValue(arg)
        }
        lastFrame = FrameState.parse(types, method, locals, stack)
    }

    private fun buildInstructions() {
        var previousNodeBlock = method.entry
        lateinit var currentBlock: BasicBlock
        for (insn in method.mn.instructions()) {
            currentBlock = nodeToBlock[insn] ?: break

            if (currentBlock != previousNodeBlock) {
                finishState(previousNodeBlock)
                previousNodeBlock = currentBlock
            }

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
        finishState(previousNodeBlock)
        buildCyclePhis()
        optimizePhis()
    }

    fun build(): Method {
        initFrame()
        buildCFG()

        if (method.isEmpty()) return method

        buildFrames()
        buildInstructions()

        RetvalBuilder(cm).visit(method)
        CfgOptimizer(cm).visit(method)
        NullTypeAdapter(cm).visit(method)

        method.slottracker.rerun()
        IRVerifier(cm).visit(method)

        return method
    }
}