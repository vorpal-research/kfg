package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.ir.value.instruction.TerminateInst
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.util.GraphNode
import org.jetbrains.research.kfg.util.defaultHashCode

abstract class BasicBlock(val name: BlockName) : Iterable<Instruction>, GraphNode<BasicBlock>, BlockUser, UsableBlock() {
    var parent: Method? = null
        internal set(value) {
            field = value
            instructions.forEach { addValueToParent(it) }
        }
    val predecessors = mutableSetOf<BasicBlock>()
    val successors = mutableSetOf<BasicBlock>()
    val instructions = mutableListOf<Instruction>()
    val handlers = mutableListOf<CatchBlock>()

    private fun addValueToParent(value: Value) {
        parent?.slottracker?.addValue(value)
    }

    fun addSuccessor(bb: BasicBlock) {
        successors.add(bb)
        bb.addUser(this)
    }

    fun addSuccessors(vararg bbs: BasicBlock) = bbs.forEach { addSuccessor(it) }
    fun addPredecessor(bb: BasicBlock) {
        predecessors.add(bb)
        bb.addUser(this)
    }

    fun addPredecessors(vararg bbs: BasicBlock) = bbs.forEach { addPredecessor(it) }
    fun addHandler(handle: CatchBlock) {
        handlers.add(handle)
        handle.addUser(this)
    }

    fun removeSuccessor(bb: BasicBlock): Boolean {
        if (successors.remove(bb)) {
            bb.removeUser(this)
            bb.removePredecessor(this)
            return true
        }
        return false
    }

    fun removePredecessor(bb: BasicBlock): Boolean {
        if (predecessors.remove(bb)) {
            bb.removeUser(this)
            bb.removeSuccessor(this)
            return true
        }
        return false
    }

    fun removeHandler(handle: CatchBlock): Boolean {
        if (handlers.remove(handle)) {
            handle.removeUser(this)
            handle.removeThrower(this)
            return true
        }
        return false
    }

    fun addInstruction(inst: Instruction) {
        instructions.add(inst)
        inst.parent = this
        addValueToParent(inst)
    }

    fun addInstructions(vararg insts: Instruction) {
        insts.forEach { addInstruction(it) }
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

    fun replace(from: Instruction, to: Instruction) {
        (0 until instructions.size).filter { instructions[it] == from }.forEach {
            instructions[it] = to
            to.parent = this
            addValueToParent(to)
        }
    }

    fun isEmpty() = instructions.isEmpty()
    fun isNotEmpty() = !isEmpty()

    fun front() = instructions.first()
    fun back() = instructions.last()
    fun getTerminator() = back() as TerminateInst

    override fun toString() = print()

    abstract fun print(): String

    override fun iterator() = instructions.iterator()

    override fun getSuccSet() = successors.toSet()
    override fun getPredSet() = predecessors.toSet()

    override fun get() = this
    override fun replaceUsesOf(from: UsableBlock, to: UsableBlock) {
        if (removePredecessor(from.get())) {
            addPredecessor(to.get())
        } else if (removeSuccessor(from.get())) {
            addSuccessor(to.get())
        } else if (handlers.contains(from.get())) {
            assert(from.get() is CatchBlock)
            val fromCatch = from.get() as CatchBlock
            removeHandler(fromCatch)
            assert(to.get() is CatchBlock)
            val toCatch = to.get() as CatchBlock
            toCatch.addThrowers(listOf(this))
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
    val throwers = mutableSetOf<MutableList<BasicBlock>>()

    fun getEntries(): Set<BasicBlock> {
        val entries = mutableSetOf<BasicBlock>()
        val throwers = getAllThrowers()
        for (it in throwers) {
            for (pred in it.predecessors)
                if (!throwers.contains(pred)) entries.add(pred)
        }
        return entries
    }

    fun addThrowers(throwers: List<BasicBlock>) {
        this.throwers.add(throwers.toMutableList())
        throwers.forEach { it.addUser(this) }
    }

    fun removeThrower(bb: BasicBlock) = this.throwers.forEach { it.remove(bb) }
    fun getAllThrowers() = throwers.flatten().toSet()
    fun getAllPredecessors() = getAllThrowers().plus(getEntries())

    override fun print(): String {
        val sb = StringBuilder()
        sb.append("$name: \t")
        val throwers = getAllThrowers()
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
        for (list in throwers) {
            val index = list.indexOf(from)
            if (index >= 0) {
                from.removeUser(this)
                list[index] = to.get()
                to.addUser(this)
            }
        }
    }
}