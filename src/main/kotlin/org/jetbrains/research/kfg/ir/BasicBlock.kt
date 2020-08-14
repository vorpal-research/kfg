package org.jetbrains.research.kfg.ir

import com.abdullin.kthelper.algorithm.Graph
import com.abdullin.kthelper.assert.asserted
import com.abdullin.kthelper.assert.ktassert
import org.jetbrains.research.kfg.ir.value.BlockName
import org.jetbrains.research.kfg.ir.value.BlockUser
import org.jetbrains.research.kfg.ir.value.UsableBlock
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.ir.value.instruction.TerminateInst
import org.jetbrains.research.kfg.type.Type

sealed class BasicBlock(val name: BlockName) : UsableBlock(), Iterable<Instruction>, Graph.Vertex<BasicBlock>, BlockUser {
    internal var parentUnsafe: Method? = null
        internal set(value) {
            field = value
            instructions.forEach { addValueToParent(it) }
        }

    val hasParent get() = parentUnsafe != null
    val parent get() = asserted(hasParent) { parentUnsafe!! }

    override val predecessors = linkedSetOf<BasicBlock>()
    override val successors = linkedSetOf<BasicBlock>()
    val instructions = arrayListOf<Instruction>()
    val handlers = arrayListOf<CatchBlock>()

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

    fun addSuccessor(bb: BasicBlock) {
        successors.add(bb)
        bb.addUser(this)
    }

    fun addSuccessors(vararg bbs: BasicBlock) = bbs.forEach { addSuccessor(it) }
    fun addSuccessors(bbs: List<BasicBlock>) = bbs.forEach { addSuccessor(it) }
    fun addPredecessor(bb: BasicBlock) {
        predecessors.add(bb)
        bb.addUser(this)
    }

    fun addPredecessors(vararg bbs: BasicBlock) = bbs.forEach { addPredecessor(it) }
    fun addPredecessors(bbs: List<BasicBlock>) = bbs.forEach { addPredecessor(it) }
    fun addHandler(handle: CatchBlock) {
        handlers.add(handle)
        handle.addUser(this)
    }

    fun removeSuccessor(bb: BasicBlock) = when {
        successors.remove(bb) -> {
            bb.removeUser(this)
            bb.removePredecessor(this)
            true
        }
        else -> false
    }

    fun removePredecessor(bb: BasicBlock): Boolean = when {
        predecessors.remove(bb) -> {
            bb.removeUser(this)
            bb.removeSuccessor(this)
            true
        }
        else -> false
    }

    fun removeHandler(handle: CatchBlock) = when {
        handlers.remove(handle) -> {
            handle.removeUser(this)
            handle.removeThrower(this)
            true
        }
        else -> false
    }

    fun add(inst: Instruction) {
        instructions.add(inst)
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
        var index = instructions.indexOf(before)
        for (inst in insts) {
            instructions.add(index++, inst)
            inst.parentUnsafe = this
            addValueToParent(inst)
        }
    }

    fun insertAfter(after: Instruction, vararg insts: Instruction) {
        var index = instructions.indexOf(after) + 1
        for (inst in insts) {
            instructions.add(index++, inst)
            inst.parentUnsafe = this
            addValueToParent(inst)
        }
    }

    fun remove(inst: Instruction) {
        if (inst.parentUnsafe == this) {
            instructions.remove(inst)
            inst.parentUnsafe = null
        }
    }

    operator fun minus(inst: Instruction): BasicBlock {
        remove(inst)
        return this
    }

    operator fun minusAssign(inst: Instruction) = remove(inst)

    fun replace(from: Instruction, to: Instruction) {
        (0..instructions.lastIndex).filter { instructions[it] == from }.forEach {
            instructions[it] = to
            to.parentUnsafe = this
            addValueToParent(to)
        }
    }

    override fun toString() = print()

    abstract fun print(): String

    override fun iterator() = instructions.iterator()

    override fun get() = this
    override fun replaceUsesOf(from: UsableBlock, to: UsableBlock) {
        when {
            removePredecessor(from.get()) -> addPredecessor(to.get())
            removeSuccessor(from.get()) -> addSuccessor(to.get())
            handlers.contains(from.get()) -> {
                ktassert(from.get() is CatchBlock)
                val fromCatch = from.get() as CatchBlock
                removeHandler(fromCatch)

                ktassert(to.get() is CatchBlock)
                val toCatch = to.get() as CatchBlock
                toCatch.addThrowers(listOf(this))
            }
        }
        terminator.replaceUsesOf(from, to)
    }

    fun replaceSuccessorUsesOf(from: UsableBlock, to: UsableBlock) {
        when {
            removeSuccessor(from.get()) -> addSuccessor(to.get())
            handlers.contains(from.get()) -> {
                ktassert(from.get() is CatchBlock)
                val fromCatch = from.get() as CatchBlock
                removeHandler(fromCatch)

                ktassert(to.get() is CatchBlock)
                val toCatch = to.get() as CatchBlock
                toCatch.addThrowers(listOf(this))
            }
        }
    }
}

class BodyBlock(name: String) : BasicBlock(BlockName(name)) {
    override fun print() = buildString {
        append("$name: \t")
        appendln("//predecessors ${predecessors.joinToString { it.name.toString() }}")
        append(instructions.joinToString(separator = "\n\t", prefix = "\t") { it.print() })
    }
}

class CatchBlock(name: String, val exception: Type) : BasicBlock(BlockName(name)) {
    val throwers = hashSetOf<BasicBlock>()

    val entries: Set<BasicBlock>
        get() {
            val entries = hashSetOf<BasicBlock>()
            for (it in throwers) {
                for (pred in it.predecessors)
                    if (pred !in throwers) entries.add(pred)
            }
            return entries
        }

    fun addThrower(thrower: BasicBlock) {
        throwers.add(thrower)
        thrower.addUser(this)
    }

    fun addThrowers(throwers: List<BasicBlock>) {
        throwers.forEach { addThrower(it) }
    }

    fun removeThrower(bb: BasicBlock) = this.throwers.remove(bb)
    val allPredecessors get() = throwers + entries

    override fun print() = buildString {
        append("$name: \t")
        appendln("//catches from ${throwers.joinToString { it.name.toString() }}")
        append(instructions.joinToString(separator = "\n", prefix = "\t") { it.print() })
    }

    companion object {
        const val defaultException = "java/lang/Throwable"
    }

    override fun replaceUsesOf(from: UsableBlock, to: UsableBlock) {
        super.replaceUsesOf(from, to)
        if (throwers.remove(from)) {
            from.removeUser(this)
            throwers.add(to.get())
            to.addUser(this)
        }
    }
}