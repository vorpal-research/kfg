package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.ir.value.instruction.TerminateInst
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kthelper.assert.asserted
import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.collection.queueOf
import org.jetbrains.research.kthelper.graph.PredecessorGraph

sealed class BasicBlock(
    val name: BlockName
) : UsableBlock(), Iterable<Instruction>, PredecessorGraph.PredecessorVertex<BasicBlock>, BlockUser {
    internal var parentUnsafe: Method? = null
        internal set(value) {
            field = value
            instructions.forEach { addValueToParent(it) }
        }

    val hasParent get() = parentUnsafe != null
    val parent get() = asserted(hasParent) { parentUnsafe!! }

    private val innerPredecessors = linkedSetOf<BasicBlock>()
    private val innerSuccessors = linkedSetOf<BasicBlock>()
    private val innerInstructions = arrayListOf<Instruction>()
    private val innerHandlers = hashSetOf<CatchBlock>()

    override val predecessors: Set<BasicBlock> get() = innerPredecessors
    override val successors: Set<BasicBlock> get() = innerSuccessors
    val instructions: List<Instruction> get() = innerInstructions
    val handlers: Set<CatchBlock> get() = innerHandlers

    val terminator: TerminateInst
        get() = last() as TerminateInst

    val isEmpty: Boolean
        get() = instructions.isEmpty()

    val isNotEmpty: Boolean
        get() = !isEmpty

    val location: Location
        get() = instructions.first().location

    val size: Int
        get() = instructions.size

    private fun addValueToParent(value: Value) {
        parentUnsafe?.slotTracker?.addValue(value)
    }

    private fun removeValueFromParent(value: Value) {
        parentUnsafe?.slotTracker?.removeValue(value)
    }

    fun addSuccessor(ctx: BlockUsageContext, bb: BasicBlock) = with(ctx) {
        innerSuccessors.add(bb)
        bb.addUser(this@BasicBlock)
    }

    fun addSuccessors(ctx: BlockUsageContext, vararg bbs: BasicBlock) = bbs.forEach { addSuccessor(ctx, it) }
    fun addSuccessors(ctx: BlockUsageContext, bbs: List<BasicBlock>) = bbs.forEach { addSuccessor(ctx, it) }
    fun addPredecessor(ctx: BlockUsageContext, bb: BasicBlock) = with(ctx) {
        innerPredecessors.add(bb)
        bb.addUser(this@BasicBlock)
    }

    fun addPredecessors(ctx: BlockUsageContext, vararg bbs: BasicBlock) = bbs.forEach { addPredecessor(ctx, it) }
    fun addPredecessors(ctx: BlockUsageContext, bbs: List<BasicBlock>) = bbs.forEach { addPredecessor(ctx, it) }
    fun addHandler(ctx: BlockUsageContext, handle: CatchBlock) = with(ctx) {
        innerHandlers.add(handle)
        handle.addUser(this@BasicBlock)
    }

    fun removeSuccessor(ctx: BlockUsageContext, bb: BasicBlock) = when {
        innerSuccessors.remove(bb) -> with(ctx) {
            bb.removeUser(this@BasicBlock)
            bb.removePredecessor(ctx, this@BasicBlock)
            true
        }
        else -> false
    }

    fun removePredecessor(ctx: BlockUsageContext, bb: BasicBlock): Boolean = when {
        innerPredecessors.remove(bb) -> with(ctx) {
            bb.removeUser(this@BasicBlock)
            bb.removeSuccessor(ctx, this@BasicBlock)
            true
        }
        else -> false
    }

    fun linkForward(ctx: UsageContext, bb: BasicBlock) = with(ctx) {
        val current = this@BasicBlock
        current.addSuccessor(bb)
        bb.addPredecessor(current)
    }

    fun linkBackward(ctx: UsageContext, bb: BasicBlock) = with(ctx) {
        val current = this@BasicBlock
        current.addPredecessor(bb)
        bb.addSuccessor(current)
    }

    fun linkThrowing(ctx: UsageContext, bb: CatchBlock) = with(ctx) {
        val current = this@BasicBlock
        current.addHandler(bb)
        bb.addThrower(current)
    }

    fun unlinkForward(ctx: UsageContext, bb: BasicBlock): Unit = with(ctx) {
        val current = this@BasicBlock
        current.removeSuccessor(ctx, bb)
        bb.removePredecessor(ctx, current)
    }

    fun unlinkBackward(ctx: UsageContext, bb: BasicBlock): Unit = with(ctx) {
        val current = this@BasicBlock
        current.removePredecessor(ctx, bb)
        bb.removeSuccessor(ctx, current)
    }

    fun unlinkThrowing(ctx: UsageContext, bb: CatchBlock) = with(ctx) {
        val current = this@BasicBlock
        current.removeHandler(bb)
        bb.removeThrower(current)
    }

    fun removeHandler(ctx: BlockUsageContext, handle: CatchBlock) = when {
        innerHandlers.remove(handle) -> with(ctx) {
            handle.removeUser(this@BasicBlock)
            handle.removeThrower(this, this@BasicBlock)
            true
        }
        else -> false
    }

    fun add(inst: Instruction) {
        innerInstructions.add(inst)
        inst.parentUnsafe = this
        addValueToParent(inst)
    }

    operator fun plus(inst: Instruction): BasicBlock {
        add(inst)
        return this
    }

    operator fun plusAssign(inst: Instruction): Unit = add(inst)

    fun addAll(vararg insts: Instruction) {
        insts.forEach { add(it) }
    }

    fun addAll(insts: List<Instruction>) {
        insts.forEach { add(it) }
    }

    fun insertBefore(before: Instruction, vararg insts: Instruction) {
        var index = innerInstructions.indexOf(before)
        for (inst in insts) {
            innerInstructions.add(index++, inst)
            inst.parentUnsafe = this
            addValueToParent(inst)
        }
    }

    fun insertAfter(after: Instruction, vararg insts: Instruction) {
        var index = innerInstructions.indexOf(after) + 1
        for (inst in insts) {
            innerInstructions.add(index++, inst)
            inst.parentUnsafe = this
            addValueToParent(inst)
        }
    }

    fun remove(inst: Instruction) {
        if (inst.parentUnsafe == this) {
            innerInstructions.remove(inst)
            inst.parentUnsafe = null
            removeValueFromParent(inst)
        }
    }

    operator fun minus(inst: Instruction): BasicBlock {
        remove(inst)
        return this
    }

    operator fun minusAssign(inst: Instruction) = remove(inst)

    fun replace(from: Instruction, to: Instruction) {
        for (index in 0..innerInstructions.lastIndex) {
            if (innerInstructions[index] == from) {
                innerInstructions[index] = to
                to.parentUnsafe = this
                addValueToParent(to)

                from.parentUnsafe = null
                removeValueFromParent(from)
            }
        }
    }

    override fun toString() = print()

    abstract fun print(): String

    override fun iterator() = instructions.iterator()

    override fun get() = this
    override fun replaceUsesOf(ctx: BlockUsageContext, from: UsableBlock, to: UsableBlock) = with(ctx) {
        if (removePredecessor(ctx, from.get())) {
            addPredecessor(ctx, to.get())
            to.get().addSuccessor(ctx, this@BasicBlock)
        }
        if (removeSuccessor(ctx, from.get())) {
            addSuccessor(ctx, to.get())
            to.get().addPredecessor(ctx, this@BasicBlock)
        }
        if (handlers.contains(from.get())) {
            ktassert(from.get() is CatchBlock)
            val fromCatch = from.get() as CatchBlock
            removeHandler(ctx, fromCatch)

            ktassert(to.get() is CatchBlock)
            val toCatch = to.get() as CatchBlock
            toCatch.addThrowers(ctx, listOf(this@BasicBlock))
        }
        terminator.replaceUsesOf(ctx, from, to)
    }

    fun replaceSuccessorUsesOf(ctx: BlockUsageContext, from: UsableBlock, to: UsableBlock) = with(ctx) {
        if (removeSuccessor(ctx, from.get())) {
            addSuccessor(ctx, to.get())
            to.get().addPredecessor(ctx, this@BasicBlock)
        }
        if (handlers.contains(from.get())) {
            ktassert(from.get() is CatchBlock)
            val fromCatch = from.get() as CatchBlock
            removeHandler(ctx, fromCatch)

            ktassert(to.get() is CatchBlock)
            val toCatch = to.get() as CatchBlock
            toCatch.addThrowers(ctx, listOf(this@BasicBlock))
        }
        terminator.replaceUsesOf(ctx, from, to)
    }
}

class BodyBlock(name: String) : BasicBlock(BlockName(name)) {
    override fun print() = buildString {
        append("$name: \t")
        appendLine("//predecessors ${predecessors.joinToString { it.name.toString() }}")
        append(instructions.joinToString(separator = "\n\t", prefix = "\t") { it.print() })
    }
}

class CatchBlock(name: String, val exception: Type) : BasicBlock(BlockName(name)) {
    private val innerThrowers = hashSetOf<BasicBlock>()
    val throwers: Set<BasicBlock> get() = innerThrowers

    val entries: Set<BasicBlock>
        get() {
            val entries = hashSetOf<BasicBlock>()
            for (it in throwers) {
                for (pred in it.predecessors)
                    if (pred !in throwers) entries.add(pred)
            }
            return entries
        }

    val body: Set<BasicBlock>
        get() {
            val catchMap = hashMapOf<BasicBlock, Boolean>()
            val visited = hashSetOf<BasicBlock>()
            val result = hashSetOf<BasicBlock>()
            val queue = queueOf<BasicBlock>(this)
            while (queue.isNotEmpty()) {
                val top = queue.poll()
                val isCatch = top.predecessors.fold(true) { acc, bb -> acc && catchMap.getOrPut(bb) { false } }
                if (isCatch && top !in visited) {
                    result.add(top)
                    queue.addAll(top.successors)
                    catchMap[top] = true
                    visited += top
                }
            }
            return result
        }

    fun addThrower(ctx: BlockUsageContext, thrower: BasicBlock) = with(ctx) {
        innerThrowers.add(thrower)
        thrower.addUser(this@CatchBlock)
    }

    fun addThrowers(ctx: BlockUsageContext, throwers: List<BasicBlock>) {
        throwers.forEach { addThrower(ctx, it) }
    }

    fun removeThrower(ctx: BlockUsageContext, bb: BasicBlock): Unit = with(ctx) {
        bb.removeUser(this@CatchBlock)
        innerThrowers.remove(bb)
    }

    fun linkCatching(ctx: UsageContext, thrower: BasicBlock) = with(ctx) {
        val current = this@CatchBlock
        current.addThrower(thrower)
        thrower.addHandler(current)
    }

    fun unlinkCatching(ctx: UsageContext, thrower: BasicBlock): Unit = with(ctx) {
        val current = this@CatchBlock
        current.removeThrower(thrower)
        thrower.removeHandler(current)
    }

    val allPredecessors get() = throwers + entries

    override fun print() = buildString {
        append("$name: \t")
        appendLine("//catches from ${throwers.joinToString { it.name.toString() }}")
        append(instructions.joinToString(separator = "\n", prefix = "\t") { it.print() })
    }

    companion object {
        const val defaultException = "java/lang/Throwable"
    }

    override fun replaceUsesOf(ctx: BlockUsageContext, from: UsableBlock, to: UsableBlock) = with(ctx) {
        super.replaceUsesOf(ctx, from, to)
        if (innerThrowers.remove(from)) {
            from.removeUser(this@CatchBlock)
            innerThrowers.add(to.get())
            to.addUser(this@CatchBlock)
        }
    }
}