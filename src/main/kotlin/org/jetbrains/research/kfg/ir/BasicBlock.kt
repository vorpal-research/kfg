package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.ir.value.instruction.TerminateInst
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.util.GraphNode

abstract class BasicBlock(val name: BlockName) : Iterable<Instruction>, GraphNode<BasicBlock>, BlockUser, UsableBlock() {
    var parent: Method? = null
        internal set(value) {
            field = value
            instructions.forEach { addValueToParent(it) }
        }
    val predecessors = hashSetOf<BasicBlock>()
    val successors = hashSetOf<BasicBlock>()
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

    private fun addValueToParent(value: Value) {
        parent?.slottracker?.addValue(value)
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
        inst.parent = this
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
            inst.parent = this
            addValueToParent(inst)
        }
    }

    fun insertAfter(after: Instruction, vararg insts: Instruction) {
        var index = instructions.indexOf(after) + 1
        for (inst in insts) {
            instructions.add(index++, inst)
            inst.parent = this
            addValueToParent(inst)
        }
    }

    fun remove(inst: Instruction) {
        if (inst.parent == this) {
            instructions.remove(inst)
            inst.parent = null
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
            to.parent = this
            addValueToParent(to)
        }
    }

    override fun toString() = print()

    abstract fun print(): String

    override fun iterator() = instructions.iterator()

    override fun getSuccSet() = successors.toSet()
    override fun getPredSet() = predecessors.toSet()

    override fun get() = this
    override fun replaceUsesOf(from: UsableBlock, to: UsableBlock) {
        when {
            removePredecessor(from.get()) -> addPredecessor(to.get())
            removeSuccessor(from.get()) -> addSuccessor(to.get())
            handlers.contains(from.get()) -> {
                require(from.get() is CatchBlock)
                val fromCatch = from.get() as CatchBlock
                removeHandler(fromCatch)

                require(to.get() is CatchBlock)
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
                require(from.get() is CatchBlock)
                val fromCatch = from.get() as CatchBlock
                removeHandler(fromCatch)

                require(to.get() is CatchBlock)
                val toCatch = to.get() as CatchBlock
                toCatch.addThrowers(listOf(this))
            }
        }
    }
}

class BodyBlock(name: String) : BasicBlock(BlockName(name)) {
    override fun print(): String {
        val sb = StringBuilder()
        sb.append("$name: \t")
        predecessors.take(1).forEach { sb.append("//predecessors ${it.name}") }
        predecessors.drop(1).forEach { sb.append(", ${it.name}") }
        sb.appendln()
        instructions.take(1).forEach { sb.append("\t${it.print()}") }
        instructions.drop(1).forEach { sb.append("\n\t${it.print()}") }
        return sb.toString()
    }
}

class CatchBlock(name: String, val exception: Type) : BasicBlock(BlockName(name)) {
    val throwers = hashSetOf<BasicBlock>()

    val entries: Set<BasicBlock>
        get() {
            val entries = hashSetOf<BasicBlock>()
            for (it in throwers) {
                for (pred in it.predecessors)
                    if (!throwers.contains(pred)) entries.add(pred)
            }
            return entries
        }

    fun addThrowers(throwers: List<BasicBlock>) {
        throwers.forEach {
            this.throwers.add(it)
            it.addUser(this)
        }
    }

    fun removeThrower(bb: BasicBlock) = this.throwers.remove(bb)
    fun getAllPredecessors() = throwers + entries

    override fun print(): String {
        val sb = StringBuilder()
        sb.append("$name: \t")
        throwers.take(1).forEach { sb.append("//catches from ${it.name}") }
        throwers.drop(1).forEach { sb.append(", ${it.name}") }
        sb.appendln()
        instructions.take(1).forEach { sb.append("\t${it.print()}") }
        instructions.drop(1).forEach { sb.append("\n\t${it.print()}") }
        return sb.toString()
    }

    companion object {
        val defaultException = TF.getRefType("java/lang/Throwable")
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