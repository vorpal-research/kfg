package org.jetbrains.research.kfg.builder.cfg

import org.jetbrains.research.kfg.*
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
import org.jetbrains.research.kthelper.`try`
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.collection.queueOf
import org.jetbrains.research.kthelper.logging.log
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.*
import java.util.*

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

        val isEmpty get() = locals.isEmpty() && stack.isEmpty()
        val isNotEmpty get() = !isEmpty

        fun clear() {
            locals.clear()
            stack.clear()
            localPhis.clear()
            stackPhis.clear()
        }
    }

    private val visitedBlocks = mutableSetOf<BasicBlock>()
    private val locals = LocalArray()
    private val nodeToBlock = hashMapOf<AbstractInsnNode, BasicBlock>()
    private val blockToNode = hashMapOf<BasicBlock, MutableList<AbstractInsnNode>>()
    private val frames = hashMapOf<BasicBlock, BlockFrame>()
    private val stack = ArrayList<Value>()
    private val unmappedBlocks = hashMapOf<BasicBlock, BlockFrame>()
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

    private fun createStackPhis(
        bb: BasicBlock,
        predFrames: List<BlockFrame>,
        stackState: SortedMap<Int, Type>
    ) {
        val predStacks = predFrames.associate { it.bb to it.stack.takeLast(stackState.size) }
        for ((index, type) in stackState) {
            val incomings = predStacks.map { it.key to it.value[index] }.toMap()
            val incomingValues = incomings.values.toSet()
            when {
                incomingValues.isEmpty() -> throw InvalidStateException("Empty incoming values map")
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
            val incomings = predFrames.associate {
                it.bb to (it.locals[local]
                    ?: unreachable { log.error("Predecessor frame does not contain a local value $local") })
            }

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
            ACONST_NULL -> values.nullConstant
            ICONST_M1 -> values.getInt(-1)
            in ICONST_0..ICONST_5 -> values.getInt(opcode - ICONST_0)
            in LCONST_0..LCONST_1 -> values.getLong((opcode - LCONST_0).toLong())
            in FCONST_0..FCONST_2 -> values.getFloat((opcode - FCONST_0).toFloat())
            in DCONST_0..DCONST_1 -> values.getDouble((opcode - DCONST_0).toDouble())
            else -> throw InvalidOpcodeException("Unknown const $opcode")
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
            else -> throw InvalidOpcodeException("Pop opcode $opcode")
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
            else -> throw InvalidOpcodeException("Dup opcode $opcode")
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
            else -> throw InvalidOpcodeException("Unary opcode ${insn.opcode}")
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
            else -> throw InvalidOpcodeException("Cast opcode ${insn.opcode}")
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
            else -> throw InvalidOpcodeException("Monitor opcode ${insn.opcode}")
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
            else -> throw InvalidOpcodeException("Insn opcode ${insn.opcode}")
        }
    }

    private fun convertIntInsn(insn: IntInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val opcode = insn.opcode
        val operand = insn.operand
        when (opcode) {
            BIPUSH -> push(values.getInt(operand))
            SIPUSH -> push(values.getInt(operand))
            NEWARRAY -> {
                val type = parsePrimaryType(types, operand)
                val count = pop()
                val inst = instructions.getNewArray(type, count)
                addInstruction(bb, inst)
                push(inst)
            }
            else -> throw InvalidOpcodeException("IntInsn opcode $opcode")
        }
    }

    private fun convertVarInsn(insn: VarInsnNode) {
        when (insn.opcode) {
            in ISTORE..ASTORE -> convertLocalStore(insn)
            in ILOAD..ALOAD -> convertLocalLoad(insn)
            RET -> throw UnsupportedOperationException("Opcode `RET`")
            else -> throw InvalidOpcodeException("VarInsn opcode ${insn.opcode}")
        }
    }

    private fun convertTypeInsn(insn: TypeInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val opcode = insn.opcode
        val type = `try` { parseDesc(types, insn.desc) }
            .getOrElse { types.getRefType(insn.desc) }
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
            else -> throw InvalidOpcodeException("$opcode in TypeInsn")
        }
    }

    private fun convertFieldInsn(insn: FieldInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val opcode = insn.opcode
        val fieldType = parseDesc(types, insn.desc)
        val klass = cm[insn.owner]
        when (opcode) {
            GETSTATIC -> {
                val field = klass.getField(insn.name, fieldType)
                val inst = instructions.getFieldLoad(field)
                addInstruction(bb, inst)
                push(inst)
            }
            PUTSTATIC -> {
                val field = klass.getField(insn.name, fieldType)
                val value = pop()
                addInstruction(bb, instructions.getFieldStore(field, value))
            }
            GETFIELD -> {
                val field = klass.getField(insn.name, fieldType)
                val owner = pop()
                val inst = instructions.getFieldLoad(owner, field)
                addInstruction(bb, inst)
                push(inst)
            }
            PUTFIELD -> {
                val value = pop()
                val owner = pop()
                val field = klass.getField(insn.name, fieldType)
                addInstruction(bb, instructions.getFieldStore(owner, field, value))
            }
        }
    }

    private fun convertMethodInsn(insn: MethodInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val klass = when {
            // FIXME: This is literally fucked up. If insn owner is an array, class of the CallInst should be java/lang/Object,
            //  because java array don't have their own methods
            insn.owner.startsWith("[") -> cm.objectClass
            else -> cm[insn.owner]
        }
        val method = klass.getMethod(insn.name, insn.desc)
//        val args = arrayListOf<Value>()
//        method.argTypes.forEach { _ -> args.add(0, pop()) }
        val args = method.argTypes.map { pop() }.reversed()

        val isNamed = !method.returnType.isVoid
        val opcode = toCallOpcode(insn.opcode)
        val call = when (insn.opcode) {
            INVOKESTATIC -> instructions.getCall(opcode, method, klass, args.toTypedArray(), isNamed)
            INVOKEVIRTUAL, INVOKESPECIAL, INVOKEINTERFACE -> {
                val obj = pop()
                instructions.getCall(opcode, method, klass, obj, args.toTypedArray(), isNamed)
            }
            else -> throw InvalidOpcodeException("Method insn opcode ${insn.opcode}")
        }
        addInstruction(bb, call)
        if (isNamed) {
            push(call)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun convertInvokeDynamicInsn(insn: InvokeDynamicInsnNode): Unit =
        throw UnsupportedOperationException("InvokeDynamicInsn")

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
                    in IFEQ..IFLE -> instructions.getCmp(name, resType, opc, rhv, values.getZero(rhv.type))
                    in IF_ICMPEQ..IF_ACMPNE -> instructions.getCmp(name, resType, opc, pop(), rhv)
                    in IFNULL..IFNONNULL -> instructions.getCmp(name, resType, opc, rhv, values.nullConstant)
                    else -> throw InvalidOpcodeException("Jump opcode ${insn.opcode}")
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
            is Int -> push(values.getInt(cst))
            is Float -> push(values.getFloat(cst))
            is Double -> push(values.getDouble(cst))
            is Long -> push(values.getLong(cst))
            is String -> push(values.getString(cst))
            is org.objectweb.asm.Type -> {
                val type = parseDesc(types, cst.descriptor)
                push(values.getClass(type))
            }
            is org.objectweb.asm.Handle -> {
                val klass = cm[cst.owner]
                val method = klass.getMethod(cst.name, cst.desc)
                push(values.getMethod(method))
            }
            else -> throw InvalidOperandException("Unknown object $cst")
        }
    }

    private fun convertIincInsn(insn: IincInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val lhv = locals[insn.`var`] ?: throw InvalidOperandException("${insn.`var`} local is invalid")
        val rhv = instructions.getBinary(BinaryOpcode.ADD, values.getInt(insn.incr), lhv)
        locals[insn.`var`] = rhv
        addInstruction(bb, rhv)
    }

    private fun convertTableSwitchInsn(insn: TableSwitchInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val index = pop()
        val min = values.getInt(insn.min)
        val max = values.getInt(insn.max)
        val default = nodeToBlock.getValue(insn.dflt)
        val branches = insn.labels.map { nodeToBlock.getValue(it) }.toTypedArray()
        addInstruction(bb, instructions.getTableSwitch(index, min, max, default, branches))
    }

    private fun convertLookupSwitchInsn(insn: LookupSwitchInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val default = nodeToBlock.getValue(insn.dflt)
        val branches = hashMapOf<Value, BasicBlock>()
        val key = pop()
        for (i in 0..insn.keys.lastIndex) {
            branches[values.getInt(insn.keys[i])] = nodeToBlock.getValue(insn.labels[i])
        }
        addInstruction(bb, instructions.getSwitch(key, default, branches))
    }

    private fun convertMultiANewArrayInsn(insn: MultiANewArrayInsnNode) {
        val bb = nodeToBlock.getValue(insn)
        val dimensions = arrayListOf<Value>()
        for (it in 0 until insn.dims)
            dimensions.add(pop())
        val type = parseDesc(types, insn.desc)
        val inst = instructions.getNewArray(type, dimensions.toTypedArray())
        addInstruction(bb, inst)
        push(inst)
    }

    private fun convertLineNumber(insn: LineNumberNode) {
        val `package` = method.klass.pkg
        val file = method.klass.cn.sourceFile ?: "Unknown"
        val line = insn.line
        currentLocation = Location(`package`, file, line)
    }

    private fun BlockFrame.getMappingFrame(frameState: FrameState): BlockFrame = unmappedBlocks.getOrPut(bb) {
        val newFrame = BlockFrame(bb)
        for ((index, type) in frameState.local) {
            newFrame.locals[index] = instructions.getPhi(type, mapOf())
        }
        for ((_, type) in frameState.stack) {
            newFrame.stack.add(instructions.getPhi(type, mapOf()))
        }
        newFrame
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
                    val mappedFrame = when {
                        predFrame.isEmpty -> predFrame.getMappingFrame(this)
                        else -> predFrame
                    }
                    for (key in this.stack.keys) {
                        push(mappedFrame.stack[key])
                    }
                    for (key in this.local.keys) {
                        locals[key] = mappedFrame.locals[key]
                            ?: throw InvalidStateException("Invalid local frame info")
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
                    val mappedFrame = when {
                        predFrame.isEmpty -> predFrame.getMappingFrame(this)
                        else -> predFrame
                    }
                    this.local.filterValues { it !is VoidType }.keys.forEach { i ->
                        locals[i] = mappedFrame.locals.getValue(i)
                    }
                }
                !isCycle -> createLocalPhis(block, predFrames, this.local)
                else -> createLocalCyclePhis(block, this.local)
            }
        }

        stack.clear()
        locals.clear()

        lastFrame = when (insn.type) {
            F_NEW -> FrameState.parse(types, method, insn)
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

    private fun buildCFG() {
        for (insn in method.mn.tryCatchBlocks) {
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
                bb = nodeToBlock.getOrPut(insn) { bb }
                insnList = blockToNode.getOrPut(bb, ::arrayListOf)

                when (insn) {
                    is JumpInsnNode -> {
                        if (insn.opcode != GOTO) {
                            val falseSuccessor = nodeToBlock.getOrPut(insn.next) { BodyBlock("if.else") }
                            bb.addSuccessor(falseSuccessor)
                            falseSuccessor.addPredecessor(bb)
                        }
                        val trueSuccessorName = if (insn.opcode == GOTO) "goto" else "if.then"
                        val trueSuccessor = nodeToBlock.getOrPut(insn.label) { BodyBlock(trueSuccessorName) }
                        bb.addSuccessor(trueSuccessor)
                        trueSuccessor.addPredecessor(bb)
                    }
                    is TableSwitchInsnNode -> {
                        val default = nodeToBlock.getOrPut(insn.dflt) { BodyBlock("tableswitch.default") }
                        bb.addSuccessors(default)
                        default.addPredecessor(bb)

                        val labels = insn.labels
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

                        val labels = insn.labels
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
            insnList.add(insn)
            method.add(bb)
        }
        for (insn in method.mn.tryCatchBlocks) {
            val handle = nodeToBlock.getValue(insn.handler) as CatchBlock
            nodeToBlock[insn.handler] = handle
            var cur: AbstractInsnNode = insn.start

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

            if (thrower !in throwers) {
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
                val incomings = predFrames.associate { it.bb to it.stack[index] }

                val newPhi = instructions.getPhi(phi.name, phi.type, incomings)
                phi.replaceAllUsesWith(newPhi)
                phi.operands.forEach { it.removeUser(phi) }
                block.replace(phi, newPhi)
            }

            for ((local, phi) in sf.localPhis) {
                val incomings = predFrames.associate {
                    it.bb to (it.locals[local]
                        ?: unreachable { log.error("Predecessor frame does not contain a local value $local") })
                }

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
                    top.clearUses()
                    if (first is PhiInst) queue.add(first)
                    top.parentUnsafe?.remove(top) ?: continue@loop
                    operands.mapNotNull { it as? PhiInst }.forEach { queue.add(it) }
                }
                incomingsSet.size == 2 && top in incomingsSet -> {
                    top.replaceAllUsesWith(
                        when (top) {
                            incomingsSet.first() -> incomingsSet.last()
                            else -> incomingsSet.first()
                        }
                    )
                    top.clearUses()
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

    private fun copyBlockMappings(block: BasicBlock) {
        val mappedFrame = unmappedBlocks.getValue(block)
        for ((index, value) in mappedFrame.locals) {
            val actualValue = locals[index]
                ?: unreachable { log.error("Block ${block.name} does not define required local $index") }
            value.replaceAllUsesWith(actualValue)
        }
        for ((index, value) in mappedFrame.stack.withIndex()) {
            val actualValue = stack[index]
            value.replaceAllUsesWith(actualValue)
        }
        unmappedBlocks.remove(block)
    }

    private fun finishState(block: BasicBlock) {
        if (block in visitedBlocks) return
        if (block !in method) return

        if (block in unmappedBlocks)
            copyBlockMappings(block)

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
            val instance = values.getThis(types.getRefType(method.klass))
            locals[localIndex++] = instance
            method.slotTracker.addValue(instance)
        }
        for ((index, type) in method.argTypes.withIndex()) {
            val arg = values.getArgument(index, method, type)
            locals[localIndex] = arg
            if (type.isDWord) localIndex += 2
            else ++localIndex
            method.slotTracker.addValue(arg)
        }
        lastFrame = FrameState.parse(types, method, locals, stack)
    }

    private fun buildInstructions() {
        var previousNodeBlock = method.entry
        lateinit var currentBlock: BasicBlock
        for (insn in method.mn.instructions) {
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
                else -> throw InvalidOpcodeException("Unknown insn: ${insn.print()}")
            }
        }
        finishState(previousNodeBlock)
        buildCyclePhis()
        optimizePhis()
    }

    fun clear() {
        visitedBlocks.clear()
        locals.clear()
        nodeToBlock.clear()
        blockToNode.clear()
        for ((_, frame) in frames) {
            frame.clear()
        }
        unmappedBlocks.clear()
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

        method.slotTracker.rerun()

        clear()

        IRVerifier(cm).visit(method)
        return method
    }
}