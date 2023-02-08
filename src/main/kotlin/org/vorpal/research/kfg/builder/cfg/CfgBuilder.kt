package org.vorpal.research.kfg.builder.cfg

import org.objectweb.asm.Opcodes
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.tree.*
import org.vorpal.research.kfg.*
import org.vorpal.research.kfg.analysis.IRVerifier
import org.vorpal.research.kfg.analysis.NullTypeAdapter
import org.vorpal.research.kfg.builder.cfg.impl.FrameStack
import org.vorpal.research.kfg.builder.cfg.impl.FrameState
import org.vorpal.research.kfg.builder.cfg.impl.LocalArray
import org.vorpal.research.kfg.ir.*
import org.vorpal.research.kfg.ir.value.AbstractUsageContext
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.ir.value.instruction.*
import org.vorpal.research.kfg.type.*
import org.vorpal.research.kfg.util.print
import org.vorpal.research.kfg.visitor.Loop
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.collection.queueOf
import org.vorpal.research.kthelper.graph.LoopDetector
import java.util.*
import org.objectweb.asm.Handle as AsmHandle
import org.objectweb.asm.Type as AsmType

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

@Suppress("UNUSED_PARAMETER")
class CfgBuilder(
    override val cm: ClassManager,
    val method: Method
) : AbstractUsageContext(), InstructionBuilder, Opcodes {
    override val ctx: UsageContext
        get() = this

    private inner class BlockFrame(val bb: BasicBlock) {
        val locals = LocalArray(this@CfgBuilder)
        val stack = FrameStack(this@CfgBuilder)

        val stackPhis = arrayListOf<PhiInst>()
        val localPhis = hashMapOf<Int, PhiInst>()

        val isEmpty get() = locals.isEmpty() && stack.isEmpty()

        @Suppress("unused")
        val isNotEmpty get() = !isEmpty

        fun clear() {
            locals.clear()
            stack.clear()
            localPhis.clear()
            stackPhis.clear()
        }
    }

    private val visitedBlocks = mutableSetOf<BasicBlock>()
    private val locals = LocalArray(this)
    private val nodeToBlock = MutableList<BasicBlock?>(method.mn.instructions.size()) { null }
    private val abstractNodeIndices = hashMapOf<AbstractInsnNode, Int>()

    private val blockToNode = hashMapOf<BasicBlock, MutableList<AbstractInsnNode>>()
    private val frames = hashMapOf<BasicBlock, BlockFrame>()
    private val stack = ArrayList<Value>()
    private val unmappedBlocks = hashMapOf<BasicBlock, BlockFrame>()
    private var currentLocation = Location()
    private val loops = mutableSetOf<Loop>()
    private lateinit var lastFrame: FrameState

    private val AbstractInsnNode.index get() = abstractNodeIndices.getOrPut(this) { method.mn.instructions.indexOf(this) }

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
                incomingValues.isEmpty() -> {
                    val newPhi = phi(type, incomings)
                    addInstruction(bb, newPhi)
                    push(newPhi)
                }

                incomingValues.size > 1 -> {
                    val newPhi = phi(type, incomings)
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
            val phi = phi(type, mapOf())
            addInstruction(bb, phi)
            push(phi)
            sf.stackPhis.add(phi as PhiInst)
        }
    }

    private fun createLocalPhis(bb: BasicBlock, predFrames: List<BlockFrame>, definedLocals: Map<Int, Type>) {
        for ((local, type) in definedLocals) {
            val incomings = predFrames.associate {
                it.bb to (it.locals[local]
                    ?: unreachable("Predecessor frame does not contain a local value $local"))
            }

            val incomingValues = incomings.values.toSet()
            when {
                incomingValues.size > 1 -> {
                    val newPhi = phi(type, incomings)
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
            val phi = phi(type, mapOf())
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

    private fun convertArrayLoad(insn: InsnNode, insnIndex: Int) {
        val bb = nodeToBlock[insnIndex]!!
        val index = pop()
        val arrayRef = pop()
        val inst = arrayRef[index]
        addInstruction(bb, inst)
        push(inst)
    }

    private fun convertArrayStore(insn: InsnNode, insnIndex: Int) {
        val bb = nodeToBlock[insnIndex]!!
        val value = pop()
        val index = pop()
        val array = pop()
        addInstruction(bb, array.store(index, value))
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

    private fun convertBinary(insn: InsnNode, insnIndex: Int) {
        val bb = nodeToBlock[insnIndex]!!
        val rhv = pop()
        val lhv = pop()
        val binOp = toBinaryOpcode(insn.opcode)
        val inst = binary(binOp, lhv, rhv)
        addInstruction(bb, inst)
        push(inst)
    }

    private fun convertUnary(insn: InsnNode, insnIndex: Int) {
        val bb = nodeToBlock[insnIndex]!!
        val operand = pop()
        val op = when (insn.opcode) {
            in INEG..DNEG -> UnaryOpcode.NEG
            ARRAYLENGTH -> UnaryOpcode.LENGTH
            else -> throw InvalidOpcodeException("Unary opcode ${insn.opcode}")
        }
        val inst = operand.unary(op)
        addInstruction(bb, inst)
        push(inst)
    }

    private fun convertCast(insn: InsnNode, insnIndex: Int) {
        val bb = nodeToBlock[insnIndex]!!
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
        val inst = op `as` type
        addInstruction(bb, inst)
        push(inst)
    }

    private fun convertCmp(insn: InsnNode, insnIndex: Int) {
        val bb = nodeToBlock[insnIndex]!!
        val rhv = pop()
        val lhv = pop()
        val op = toCmpOpcode(insn.opcode)
        val inst = cmp(op, lhv, rhv)
        addInstruction(bb, inst)
        push(inst)
    }

    private fun convertReturn(insn: InsnNode, insnIndex: Int) {
        val bb = nodeToBlock[insnIndex]!!
        when (RETURN) {
            insn.opcode -> addInstruction(bb, `return`())
            else -> addInstruction(bb, `return`(pop()))
        }
    }

    private fun convertMonitor(insn: InsnNode, insnIndex: Int) {
        val bb = nodeToBlock[insnIndex]!!
        val owner = pop()
        when (insn.opcode) {
            MONITORENTER -> addInstruction(bb, owner.lock())
            MONITOREXIT -> addInstruction(bb, owner.unlock())
            else -> throw InvalidOpcodeException("Monitor opcode ${insn.opcode}")
        }
    }

    private fun convertThrow(insn: InsnNode, insnIndex: Int) {
        val bb = nodeToBlock[insnIndex]!!
        val throwable = pop()
        addInstruction(bb, throwable.`throw`())
    }

    private fun convertLocalLoad(insn: VarInsnNode) {
        push(locals[insn.`var`]!!)
    }

    private fun convertLocalStore(insn: VarInsnNode) {
        locals[insn.`var`] = pop()
    }

    private fun convertInsn(insn: InsnNode, insnIndex: Int) {
        when (insn.opcode) {
            NOP -> Unit
            in ACONST_NULL..DCONST_1 -> convertConst(insn)
            in IALOAD..SALOAD -> convertArrayLoad(insn, insnIndex)
            in IASTORE..SASTORE -> convertArrayStore(insn, insnIndex)
            in POP..POP2 -> convertPop(insn)
            in DUP..DUP2_X2 -> convertDup(insn)
            SWAP -> convertSwap(insn)
            in IADD..DREM -> convertBinary(insn, insnIndex)
            in INEG..DNEG -> convertUnary(insn, insnIndex)
            in ISHL..LXOR -> convertBinary(insn, insnIndex)
            in I2L..I2S -> convertCast(insn, insnIndex)
            in LCMP..DCMPG -> convertCmp(insn, insnIndex)
            in IRETURN..RETURN -> convertReturn(insn, insnIndex)
            ARRAYLENGTH -> convertUnary(insn, insnIndex)
            ATHROW -> convertThrow(insn, insnIndex)
            in MONITORENTER..MONITOREXIT -> convertMonitor(insn, insnIndex)
            else -> throw InvalidOpcodeException("Insn opcode ${insn.opcode}")
        }
    }

    private fun convertIntInsn(insn: IntInsnNode, insnIndex: Int) {
        val bb = nodeToBlock[insnIndex]!!
        val opcode = insn.opcode
        val operand = insn.operand
        when (opcode) {
            BIPUSH -> push(values.getInt(operand))
            SIPUSH -> push(values.getInt(operand))
            NEWARRAY -> {
                val type = parsePrimaryType(types, operand)
                val count = pop()
                val inst = type.newArray(count)
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

    private fun convertTypeInsn(insn: TypeInsnNode, insnIndex: Int) {
        val bb = nodeToBlock[insnIndex]!!
        val opcode = insn.opcode
        val type = parseDescOrNull(types, insn.desc) ?: types.getRefType(insn.desc)
        when (opcode) {
            NEW -> {
                val inst = type.new()
                addInstruction(bb, inst)
                push(inst)
            }

            ANEWARRAY -> {
                val count = pop()
                val inst = type.newArray(count)
                addInstruction(bb, inst)
                push(inst)
            }

            CHECKCAST -> {
                val castedValue = pop()
                val inst = castedValue `as` type
                addInstruction(bb, inst)
                push(inst)
            }

            INSTANCEOF -> {
                val obj = pop()
                val inst = obj `is` type
                addInstruction(bb, inst)
                push(inst)
            }

            else -> throw InvalidOpcodeException("$opcode in TypeInsn")
        }
    }

    private fun convertFieldInsn(insn: FieldInsnNode, insnIndex: Int) {
        val bb = nodeToBlock[insnIndex]!!
        val opcode = insn.opcode
        val fieldType = parseDescOrNull(types, insn.desc) ?: unreachable("Unexpected type desc: ${insn.desc}")
        val klass = cm[insn.owner]
        when (opcode) {
            GETSTATIC -> {
                val field = klass.getField(insn.name, fieldType)
                val inst = field.load()
                addInstruction(bb, inst)
                push(inst)
            }

            PUTSTATIC -> {
                val field = klass.getField(insn.name, fieldType)
                val value = pop()
                addInstruction(bb, field.store(value))
            }

            GETFIELD -> {
                val field = klass.getField(insn.name, fieldType)
                val owner = pop()
                val inst = owner.load(field)
                addInstruction(bb, inst)
                push(inst)
            }

            PUTFIELD -> {
                val value = pop()
                val owner = pop()
                val field = klass.getField(insn.name, fieldType)
                addInstruction(bb, owner.store(field, value))
            }
        }
    }

    private fun convertMethodInsn(insn: MethodInsnNode, insnIndex: Int) {
        val bb = nodeToBlock[insnIndex]!!
        val klass = when {
            // FIXME: This is literally fucked up. If insn owner is an array, class of the CallInst should be java/lang/Object,
            //  because java array don't have their own methods
            insn.owner.startsWith("[") -> cm.objectClass
            else -> cm[insn.owner]
        }
        val method = klass.getMethod(insn.name, insn.desc)
        val args = method.argTypes.map { pop() }.reversed()

        val isNamed = !method.returnType.isVoid
        val call = when (insn.opcode) {
            INVOKESTATIC -> method.staticCall(klass, args)
            else -> {
                val instance = pop()
                when (insn.opcode) {
                    INVOKEVIRTUAL -> method.virtualCall(klass, instance, args)
                    INVOKESPECIAL -> method.specialCall(klass, instance, args)
                    INVOKEINTERFACE -> method.interfaceCall(klass, instance, args)
                    else -> throw InvalidOpcodeException("Method insn opcode ${insn.opcode}")
                }
            }
        }
        addInstruction(bb, call)
        if (isNamed) {
            push(call)
        }
    }

    private val AsmHandle.asHandle: Handle
        get() = Handle(this.tag, cm[this.owner].getMethod(this.name, this.desc), this.isInterface)

    private val AsmType.asKfgType: Any
        get() = when (this.sort) {
            org.objectweb.asm.Type.VOID -> types.voidType
            org.objectweb.asm.Type.BOOLEAN -> types.boolType
            org.objectweb.asm.Type.CHAR -> types.charType
            org.objectweb.asm.Type.BYTE -> types.byteType
            org.objectweb.asm.Type.SHORT -> types.shortType
            org.objectweb.asm.Type.INT -> types.intType
            org.objectweb.asm.Type.FLOAT -> types.floatType
            org.objectweb.asm.Type.LONG -> types.longType
            org.objectweb.asm.Type.DOUBLE -> types.doubleType
            org.objectweb.asm.Type.ARRAY -> types.getArrayType(this.elementType.asKfgType as Type)
            org.objectweb.asm.Type.OBJECT -> cm[this.className.replace('.', '/')].asType
            org.objectweb.asm.Type.METHOD -> MethodDescriptor(this.argumentTypes.map { it.asKfgType }
                .map { it as Type }, this.returnType.asKfgType as Type)

            else -> unreachable("Unknown type: $this")
        }

    @Suppress("UNUSED_PARAMETER")
    private fun convertInvokeDynamicInsn(insn: InvokeDynamicInsnNode, insnIndex: Int) {
        val bb = nodeToBlock[insnIndex]!!
        val desc = MethodDescriptor.fromDesc(types, insn.desc)
        val bsmMethod = insn.bsm.asHandle
        val bsmArgs = insn.bsmArgs.map {
            when (it) {
                is Number -> it.asValue
                is String -> it.asValue
                is AsmType -> it.asKfgType
                is AsmHandle -> it.asHandle
                else -> unreachable("Unknown arg of bsm: $it")
            }
        }.reversed()
        val args = desc.args.map { pop() }.reversed()
        val invokeDynamic = invokeDynamic(insn.name, desc, bsmMethod, bsmArgs, args)
        addInstruction(bb, invokeDynamic)
        push(invokeDynamic)
    }

    private fun convertJumpInsn(insn: JumpInsnNode, insnIndex: Int) {
        val bb = nodeToBlock[insnIndex]!!

        when (insn.opcode) {
            GOTO -> {
                val trueSuccessor = nodeToBlock[insn.label.index]!!
                addInstruction(bb, goto(trueSuccessor))
            }

            else -> {
                val falseSuccessor = nodeToBlock[insnIndex + 1]!!
                val trueSuccessor = nodeToBlock[insn.label.index]!!
                val rhv = pop()
                val opc = toCmpOpcode(insn.opcode)
                val cond = when (insn.opcode) {
                    in IFEQ..IFLE -> cmp(opc, rhv, values.getZero(rhv.type))
                    in IF_ICMPEQ..IF_ACMPNE -> cmp(opc, pop(), rhv)
                    in IFNULL..IFNONNULL -> cmp(opc, rhv, values.nullConstant)
                    else -> throw InvalidOpcodeException("Jump opcode ${insn.opcode}")
                }
                addInstruction(bb, cond)
                val castedCond = if (cond.type is BoolType) cond else cond `as` types.boolType
                addInstruction(bb, ite(castedCond, trueSuccessor, falseSuccessor))
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
            is AsmType -> {
                val type = parseDescOrNull(types, cst.descriptor)
                    ?: unreachable("Unexpected type desc: ${cst.descriptor}")
                push(values.getClass(type))
            }

            is AsmHandle -> {
                val klass = cm[cst.owner]
                val method = klass.getMethod(cst.name, cst.desc)
                push(values.getMethod(method))
            }

            else -> throw InvalidOperandException("Unknown object $cst")
        }
    }

    private fun convertIincInsn(insn: IincInsnNode, insnIndex: Int) {
        val bb = nodeToBlock[insnIndex]!!
        val lhv = locals[insn.`var`] ?: throw InvalidOperandException("${insn.`var`} local is invalid")
        val rhv = lhv + insn.incr
        locals[insn.`var`] = rhv
        addInstruction(bb, rhv)
    }

    private fun convertTableSwitchInsn(insn: TableSwitchInsnNode, insnIndex: Int) {
        val bb = nodeToBlock[insnIndex]!!
        val index = pop()
        val default = nodeToBlock[insn.dflt.index]!!
        val branches = insn.labels.map { nodeToBlock[it.index]!! }
        addInstruction(bb, index.tableSwitch(insn.min..insn.max, branches, default))
    }

    private fun convertLookupSwitchInsn(insn: LookupSwitchInsnNode, insnIndex: Int) {
        val bb = nodeToBlock[insnIndex]!!
        val default = nodeToBlock[insn.dflt.index]!!
        val branches = hashMapOf<Value, BasicBlock>()
        val key = pop()
        for (i in 0..insn.keys.lastIndex) {
            branches[values.getInt(insn.keys[i])] = nodeToBlock[insn.labels[i].index]!!
        }
        addInstruction(bb, key.switch(branches, default))
    }

    private fun convertMultiANewArrayInsn(insn: MultiANewArrayInsnNode, insnIndex: Int) {
        val bb = nodeToBlock[insnIndex]!!
        val dimensions = arrayListOf<Value>()
        for (it in 0 until insn.dims)
            dimensions.add(pop())
        val type = parseDescOrNull(types, insn.desc)
            ?: unreachable("Unexpected type desc: ${insn.desc}")
        val inst = type.newArray(dimensions)
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
            newFrame.locals[index] = phi(type, mapOf())
        }
        for ((_, type) in frameState.stack) {
            newFrame.stack.add(phi(type, mapOf()))
        }
        newFrame
    }

    @Suppress("UNUSED_PARAMETER")
    private fun convertFrame(insn: FrameNode, insnIndex: Int) {
        val block = nodeToBlock[insnIndex]!!
        val predecessors = when (block) {
            is CatchBlock -> block.allPredecessors
            else -> block.predecessors
        }
        val isLoopHeader = loops.any { it.header == block }
        val isCatch = predecessors.any { it !in visitedBlocks }
        val isCycle = isLoopHeader || isCatch
        val predFrames = predecessors.map { frames.getValue(it) }

        fun FrameState.copyStackAndLocals(predFrames: List<BlockFrame>) {
            when {
                predFrames.isEmpty() -> {
                    val frame = frames.getValue(block)
                    for ((_, element) in this.stack) {
                        val generated = phi(element, mapOf())
                        push(generated)
                        frame.stack.add(generated)
                    }
                    for ((key, element) in this.local) {
                        val generated = phi(element, mapOf())
                        locals[key] = generated
                        frame.locals[key] = generated
                    }
                }

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
                predFrames.isEmpty() -> {
                    val frame = frames.getValue(block)
                    for ((key, element) in this.local) {
                        val generated = phi(element, mapOf())
                        locals[key] = generated
                        frame.locals[key] = generated
                    }
                }

                predFrames.size == 1 -> {
                    val predFrame = predFrames.first()
                    val mappedFrame = when {
                        predFrame.isEmpty -> predFrame.getMappingFrame(this)
                        else -> predFrame
                    }
                    this.local.filterValues { it !is VoidType }.keys.forEach { i ->
                        locals[i] = mappedFrame.locals.getValue(i)
                    }
                    if (mappedFrame !== predFrame) {
                        mappedFrame.clear()
                    }
                }

                !isCycle -> createLocalPhis(block, predFrames, this.local)
                else -> createLocalCyclePhis(block, this.local)
            }
        }

        stack.clear()
        locals.clear()

        lastFrame = when (insn.frameType) {
            F_NEW -> FrameState.parse(types, method, insn)
            F_FULL -> FrameState.parse(types, method, insn)
            F_APPEND -> lastFrame.appendFrame(insn)
            F_CHOP -> lastFrame.dropFrame(insn)
            F_SAME -> lastFrame.copy()
            F_SAME1 -> lastFrame.copy1(insn)
            else -> unreachable("Unknown frame node type $insn")
        }

        when (block) {
            is CatchBlock -> {
                lastFrame.copyLocals(predFrames)

                val inst = block.exception.catch()
                addInstruction(block, inst)
                push(inst)
            }

            else -> {
                lastFrame.copyStackAndLocals(predFrames)
            }
        }
    }

    private inline fun <T> MutableList<T?>.getOrSet(index: Int, block: () -> T): T {
        if (this[index] == null)
            this[index] = block()
        return this[index]!!
    }

    private fun buildCFG(body: MethodBody) {
        for (insn in method.mn.tryCatchBlocks) {
            val type = when {
                insn.type != null -> types.getRefType(insn.type)
                else -> types.getRefType(CatchBlock.defaultException)
            }
            nodeToBlock[insn.handler.index] = CatchBlock("catch", type)
        }

        var bb: BasicBlock = BodyBlock("bb")
        var insnList = blockToNode.getOrPut(bb, ::arrayListOf)

        for ((insnIndex, insn) in method.mn.instructions.withIndex()) {
            if (insn is LabelNode) {
                when {
                    insn.next == null -> Unit
                    insn.previous == null -> {
                        // register entry block if first insn of method is a label
                        bb = nodeToBlock.getOrSet(insnIndex) { bb }

                        val entry = BodyBlock("entry")
                        addInstruction(entry, goto(bb))
                        blockToNode[entry] = arrayListOf()
                        entry.linkForward(bb)

                        body.add(entry)
                    }

                    else -> {
                        bb = nodeToBlock.getOrSet(insnIndex) { BodyBlock("label") }
                        insnList = blockToNode.getOrPut(bb, ::arrayListOf)

                        if (!insn.previous.isTerminate) {
                            val prev = nodeToBlock[insnIndex - 1]!!
                            bb.linkBackward(prev)
                        }
                    }
                }
            } else {
                bb = nodeToBlock.getOrSet(insnIndex) { bb }
                insnList = blockToNode.getOrPut(bb, ::arrayListOf)

                when (insn) {
                    is JumpInsnNode -> {
                        if (insn.opcode != GOTO) {
                            val falseSuccessor = nodeToBlock.getOrSet(insnIndex + 1) { BodyBlock("if.else") }
                            bb.linkForward(falseSuccessor)
                        }
                        val trueSuccessorName = if (insn.opcode == GOTO) "goto" else "if.then"
                        val trueSuccessor = nodeToBlock.getOrSet(insn.label.index) {
                            BodyBlock(trueSuccessorName)
                        }
                        bb.linkForward(trueSuccessor)
                    }

                    is TableSwitchInsnNode -> {
                        val default =
                            nodeToBlock.getOrSet(insn.dflt.index) {
                                BodyBlock("tableswitch.default")
                            }
                        bb.linkForward(default)

                        val labels = insn.labels
                        for (lbl in labels) {
                            val lblBB = nodeToBlock.getOrSet(lbl.index) {
                                BodyBlock("tableswitch")
                            }
                            bb.linkForward(lblBB)
                        }
                    }

                    is LookupSwitchInsnNode -> {
                        val default = nodeToBlock.getOrSet(insn.dflt.index) {
                            BodyBlock("switch.default")
                        }
                        bb.linkForward(default)

                        val labels = insn.labels
                        for (lbl in labels) {
                            val lblBB = nodeToBlock.getOrSet(lbl.index) {
                                BodyBlock("switch")
                            }
                            bb.linkForward(lblBB)
                        }
                    }

                    else -> {
                        if (insn.throwsException && (insn.next != null)) {
                            val next = nodeToBlock.getOrSet(insnIndex + 1) { BodyBlock("bb") }
                            if (!insn.isTerminate) {
                                bb.linkForward(next)
                            }
                        }
                    }
                }
            }
            insnList.add(insn)
            body.add(bb)
        }
        for (insn in method.mn.tryCatchBlocks) {
            val handleIndex = insn.handler.index
            val handle = nodeToBlock[handleIndex] as CatchBlock
            var cur: Int = insn.start.index

            var thrower = nodeToBlock[cur]!!
            val throwers = arrayListOf<BasicBlock>()
            while (method.mn.instructions[cur] != insn.end) {
                bb = nodeToBlock[cur]!!
                if (bb.name != thrower.name) {
                    throwers.add(thrower)
                    thrower.addHandler(handle)
                    thrower = bb
                }
                cur++
            }

            if (thrower !in throwers) {
                throwers.add(thrower)
                thrower.addHandler(handle)
            }
            handle.addThrowers(throwers)
            body.addCatchBlock(handle)
        }
    }

    private fun buildFrames(body: MethodBody) {
        if (body.isEmpty()) return
        val sf = frames.getOrPut(body.entry) { BlockFrame(body.entry) }
        sf.locals.putAll(locals)

        for (bb in body.basicBlocks.drop(1)) {
            frames.getOrPut(bb) { BlockFrame(bb) }
        }
    }

    private fun buildCyclePhis(body: MethodBody) {
        for (block in body) {
            val sf = frames.getValue(block)
            val predFrames = when (block) {
                is CatchBlock -> block.allPredecessors.map { frames.getValue(it) }
                else -> block.predecessors.map { frames.getValue(it) }
            }

            for ((index, phi) in sf.stackPhis.withIndex()) {
                val incomings = predFrames.associate { it.bb to it.stack[index] }

                val clearStubs = incomings.filterValues {
                    if (it !is PhiInst) true
                    else it.hasParent
                }

                if (clearStubs.size == 1) {
                    val actual = clearStubs.values.first()
                    phi.replaceAllUsesWith(actual)
                    phi.operands.forEach { it.removeUser(phi) }
                    block.remove(phi)
                    phi.clearAllUses()
                } else {
                    val newPhi = phi(phi.name, phi.type, incomings)
                    phi.replaceAllUsesWith(newPhi)
                    phi.operands.forEach { it.removeUser(phi) }
                    block.replace(phi, newPhi)
                    phi.clearAllUses()
                }
            }

            for ((local, phi) in sf.localPhis) {
                val incomings = predFrames.associate {
                    it.bb to (it.locals[local]
                        ?: unreachable("Predecessor frame does not contain a local value $local"))
                }

                val clearStubs = incomings.filterValues {
                    if (it !is PhiInst) true
                    else it.hasParent
                }

                if (clearStubs.size == 1) {
                    val actual = clearStubs.values.first()
                    phi.replaceAllUsesWith(actual)
                    phi.operands.forEach { it.removeUser(phi) }
                    block.remove(phi)
                    phi.clearAllUses()
                } else {
                    val newPhi = phi(phi.type, incomings)
                    phi.replaceAllUsesWith(newPhi)
                    phi.operands.forEach { it.removeUser(phi) }
                    block.replace(phi, newPhi)
                    phi.clearAllUses()
                }
            }
        }
    }

    private fun optimizePhis(body: MethodBody) {
        val queue = queueOf(body.flatten().filterIsInstance<PhiInst>())
        for ((deps, values) in queue.groupBy { it.users.filterIsInstance<Value>() }) {
            if (deps == values) {
                for (value in values) {
                    value?.parentUnsafe?.remove(value)
                    queue.remove(value)
                    value.clearAllUses()
                }
            }
        }
        loop@ while (queue.isNotEmpty()) {
            val top = queue.poll()
            val incomings = top.incomingValues
            val incomingsSet = incomings.toSet()
            val instUsers = top.users.filterIsInstance<Instruction>()
            val operands = top.operands

            when {
                incomingsSet.size == 1 -> {
                    val first = incomingsSet.first()
                    if (first == top) continue
                    top.replaceAllUsesWith(first)
                    top.clearAllUses()
                    if (first is PhiInst) queue.add(first)
                    top.parentUnsafe?.remove(top) ?: continue@loop
                    for (operand in operands) {
                        if (operand is PhiInst) {
                            queue.add(operand)
                        }
                    }
                }

                incomingsSet.size == 2 && top in incomingsSet -> {
                    top.replaceAllUsesWith(
                        when (top) {
                            incomingsSet.first() -> incomingsSet.last()
                            else -> incomingsSet.first()
                        }
                    )
                    top.clearAllUses()
                    for (operand in instUsers) {
                        if (operand is PhiInst) {
                            queue.add(operand)
                        }
                    }
                }

                instUsers.isEmpty() -> {
                    top.clearAllUses()
                    top.parentUnsafe?.remove(top) ?: continue@loop
                    for (operand in operands) {
                        if (operand is PhiInst) {
                            queue.add(operand)
                        }
                    }
                }

                instUsers.size == 1 && instUsers.first() == top -> {
                    top.clearAllUses()
                    top.parentUnsafe?.remove(top)
                    for (operand in operands) {
                        if (operand is PhiInst && operand == top) {
                            queue.add(operand)
                        }
                    }
                }
            }
        }
    }

    private fun copyBlockMappings(block: BasicBlock) {
        val mappedFrame = unmappedBlocks.getValue(block)
        for ((index, value) in mappedFrame.locals) {
            val actualValue = locals[index]
                ?: unreachable("Block ${block.name} does not define required local $index")
            value.replaceAllUsesWith(actualValue)
        }
        for ((index, value) in mappedFrame.stack.toList().withIndex()) {
            val actualValue = stack[index]
            value.replaceAllUsesWith(actualValue)
        }
        mappedFrame.clear()
        unmappedBlocks.remove(block)
    }

    private fun finishState(body: MethodBody, block: BasicBlock) {
        if (block in visitedBlocks) return
        if (block !in body) return

        if (block in unmappedBlocks)
            copyBlockMappings(block)

        val last = block.instructions.lastOrNull()
        if (last == null || !last.isTerminate) {
            when (block.successors.size) {
                1 -> addInstruction(block, goto(block.successors.first()))
                0 -> {}
                else -> unreachable("Unexpected block in finish")
            }
        }
        reserveState(block)
        visitedBlocks += block
    }

    private fun initFrame(body: MethodBody) {
        var localIndex = 0
        if (!method.isStatic) {
            val instance = values.getThis(types.getRefType(method.klass))
            locals[localIndex++] = instance
            body.slotTracker.addValue(instance)
        }
        for ((index, type) in method.argTypes.withIndex()) {
            val arg = values.getArgument(index, method, type)
            locals[localIndex] = arg
            if (type.isDWord) localIndex += 2
            else ++localIndex
            body.slotTracker.addValue(arg)
        }
        lastFrame = FrameState.parse(types, method, locals, stack)
    }

    private fun buildInstructions(body: MethodBody) {
        var previousNodeBlock = body.entry
        lateinit var currentBlock: BasicBlock
        for ((insnIndex, insn) in method.mn.instructions.withIndex()) {
            currentBlock = nodeToBlock[insnIndex] ?: break

            if (currentBlock != previousNodeBlock) {
                finishState(body, previousNodeBlock)
                previousNodeBlock = currentBlock
            }

            when (insn) {
                is InsnNode -> convertInsn(insn, insnIndex)
                is IntInsnNode -> convertIntInsn(insn, insnIndex)
                is VarInsnNode -> convertVarInsn(insn)
                is TypeInsnNode -> convertTypeInsn(insn, insnIndex)
                is FieldInsnNode -> convertFieldInsn(insn, insnIndex)
                is MethodInsnNode -> convertMethodInsn(insn, insnIndex)
                is InvokeDynamicInsnNode -> convertInvokeDynamicInsn(insn, insnIndex)
                is JumpInsnNode -> convertJumpInsn(insn, insnIndex)
                is LabelNode -> convertLabel(insn)
                is LdcInsnNode -> convertLdcInsn(insn)
                is IincInsnNode -> convertIincInsn(insn, insnIndex)
                is TableSwitchInsnNode -> convertTableSwitchInsn(insn, insnIndex)
                is LookupSwitchInsnNode -> convertLookupSwitchInsn(insn, insnIndex)
                is MultiANewArrayInsnNode -> convertMultiANewArrayInsn(insn, insnIndex)
                is LineNumberNode -> convertLineNumber(insn)
                is FrameNode -> convertFrame(insn, insnIndex)
                else -> throw InvalidOpcodeException("Unknown insn: ${insn.print()}")
            }
        }
        finishState(body, previousNodeBlock)
        buildCyclePhis(body)
        optimizePhis(body)
    }

    private fun clearUses(body: MethodBody) {
        visitedBlocks.clear()
        locals.clear()
        nodeToBlock.clear()
        blockToNode.clear()
        for ((_, frame) in frames) {
            frame.clear()
        }
        frames.clear()
        for ((_, frame) in unmappedBlocks) {
            frame.clear()
        }
        unmappedBlocks.clear()

        for (inst in body.flatten()) {
            for (value in (inst.operands + inst)) {
                value.users.filterNot { it is Instruction }.forEach {
                    value.removeUser(it)
                }
            }
        }
    }

    private fun buildLoops(body: MethodBody) {
        loops.addAll(LoopDetector(body).search().map { Loop(it.key, it.value.toMutableSet()) })
    }

    fun build(): MethodBody {
        val body = MethodBody(method)
        initFrame(body)
        buildCFG(body)

        if (body.isEmpty()) return body.also { clear() }

        buildLoops(body)
        buildFrames(body)
        buildInstructions(body)

        clearUses(body)

        RetvalBuilder(cm, this).visitBody(body)
        CfgOptimizer(cm, this).visitBody(body)
        NullTypeAdapter(cm, this).visitBody(body)
        ThrowCatchNormalizer(cm, this).visitBody(body)

        body.slotTracker.rerun()

        IRVerifier(cm, this).visitBody(body)

        clear()
        return body
    }
}
