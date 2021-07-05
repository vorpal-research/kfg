package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.ir.value.BlockName
import org.jetbrains.research.kfg.ir.value.BlockUser
import org.jetbrains.research.kfg.ir.value.UsableBlock
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.ir.value.instruction.TerminateInst
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kthelper.algorithm.PredecessorGraph
import org.jetbrains.research.kthelper.assert.asserted
import org.jetbrains.research.kthelper.assert.ktassert

sealed class BasicBlock(
    val name: BlockName
) : UsableBlock(), Iterable<Instruction>, PredecessorGraph.PredecessorVertex<BasicBlock>, BlockUser {
    internal var parentUnsafe: Method? = null

    val hasParent get() = parentUnsafe != null
    val parent get() = asserted(hasParent) { parentUnsafe!! }

    private val innerPredecessors = linkedSetOf<BasicBlock>()
    private val innerSuccessors = linkedSetOf<BasicBlock>()
    private val innerInstructions = arrayListOf<Instruction>()
    private val innerHandlers = arrayListOf<CatchBlock>()

    override val predecessors: Set<BasicBlock> get() = innerPredecessors
    override val successors: Set<BasicBlock> get() = innerSuccessors
    val instructions: List<Instruction> get() = innerInstructions
    val handlers: List<CatchBlock> get() = innerHandlers

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
        parentUnsafe?.namesGenerated = false
    }

    private fun removeValueFromParent(value: Value) {
        parentUnsafe?.namesGenerated = false
    }

    fun addSuccessor(bb: BasicBlock) {
        innerSuccessors.add(bb)
        bb.addUser(this)
    }

    fun addSuccessors(vararg bbs: BasicBlock) = bbs.forEach { addSuccessor(it) }
    fun addSuccessors(bbs: List<BasicBlock>) = bbs.forEach { addSuccessor(it) }
    fun addPredecessor(bb: BasicBlock) {
        innerPredecessors.add(bb)
        bb.addUser(this)
    }

    fun addPredecessors(vararg bbs: BasicBlock) = bbs.forEach { addPredecessor(it) }
    fun addPredecessors(bbs: List<BasicBlock>) = bbs.forEach { addPredecessor(it) }
    fun addHandler(handle: CatchBlock) {
        innerHandlers.add(handle)
        handle.addUser(this)
    }

    fun removeSuccessor(bb: BasicBlock) = when {
        innerSuccessors.remove(bb) -> {
            bb.removeUser(this)
            bb.removePredecessor(this)
            true
        }
        else -> false
    }

    fun removePredecessor(bb: BasicBlock): Boolean = when {
        innerPredecessors.remove(bb) -> {
            bb.removeUser(this)
            bb.removeSuccessor(this)
            true
        }
        else -> false
    }

    fun removeHandler(handle: CatchBlock) = when {
        innerHandlers.remove(handle) -> {
            handle.removeUser(this)
            handle.removeThrower(this)
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
    override fun replaceUsesOf(from: UsableBlock, to: UsableBlock) {
        if (removePredecessor(from.get())) {
            addPredecessor(to.get())
            to.get().addSuccessor(this)
        }
        if (removeSuccessor(from.get())) {
            addSuccessor(to.get())
            to.get().addPredecessor(this)
        }
        if (handlers.contains(from.get())) {
            ktassert(from.get() is CatchBlock)
            val fromCatch = from.get() as CatchBlock
            removeHandler(fromCatch)

            ktassert(to.get() is CatchBlock)
            val toCatch = to.get() as CatchBlock
            toCatch.addThrowers(listOf(this))
        }
        terminator.replaceUsesOf(from, to)
    }

    fun replaceSuccessorUsesOf(from: UsableBlock, to: UsableBlock) {
        if (removeSuccessor(from.get())) {
            addSuccessor(to.get())
            to.get().addPredecessor(this)
        }
        if (handlers.contains(from.get())) {
            ktassert(from.get() is CatchBlock)
            val fromCatch = from.get() as CatchBlock
            removeHandler(fromCatch)

            ktassert(to.get() is CatchBlock)
            val toCatch = to.get() as CatchBlock
            toCatch.addThrowers(listOf(this))
        }
        terminator.replaceUsesOf(from, to)
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

    fun addThrower(thrower: BasicBlock) {
        innerThrowers.add(thrower)
        thrower.addUser(this)
    }

    fun addThrowers(throwers: List<BasicBlock>) {
        throwers.forEach { addThrower(it) }
    }

    fun removeThrower(bb: BasicBlock) = innerThrowers.remove(bb)
    val allPredecessors get() = throwers + entries

    override fun print() = buildString {
        append("$name: \t")
        appendLine("//catches from ${throwers.joinToString { it.name.toString() }}")
        append(instructions.joinToString(separator = "\n", prefix = "\t") { it.print() })
    }

    companion object {
        const val defaultException = "java/lang/Throwable"
    }

    override fun replaceUsesOf(from: UsableBlock, to: UsableBlock) {
        super.replaceUsesOf(from, to)
        if (innerThrowers.remove(from)) {
            from.removeUser(this)
            innerThrowers.add(to.get())
            to.addUser(this)
        }
    }
}