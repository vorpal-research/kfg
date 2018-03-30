package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.util.GraphNode
import org.jetbrains.research.kfg.util.defaultHashCode

abstract class BasicBlock(val name: BlockName, val parent: Method): Iterable<Instruction>, GraphNode {
    val predecessors = mutableSetOf<BasicBlock>()
    val successors = mutableSetOf<BasicBlock>()
    val instructions = mutableListOf<Instruction>()
    val handlers = mutableListOf<CatchBlock>()

    fun addSuccessor(bb: BasicBlock) = successors.add(bb)
    fun addSuccessors(vararg bbs: BasicBlock) = successors.addAll(bbs)
    fun addPredecessor(bb: BasicBlock) = predecessors.add(bb)
    fun addPredecessors(vararg bbs: BasicBlock) = predecessors.addAll(bbs)
    fun addHandler(handle: CatchBlock) = handlers.add(handle)

    fun addInstruction(inst: Instruction) {
        instructions.add(inst)
        inst.parent = this
        parent.slottracker.addValue(inst)
    }

    fun addInstructions(vararg insts: Instruction) {
        insts.forEach { addInstruction(it) }
    }

    fun insertBefore(before: Instruction, vararg insts: Instruction) {
        var index = instructions.indexOf(before)
        for (inst in insts) {
            instructions.add(index++, inst)
            inst.parent = this
            parent.slottracker.addValue(inst)
        }
    }

    fun insertAfter(after: Instruction, vararg insts: Instruction) {
        var index = instructions.indexOf(after) + 1
        for (inst in insts) {
            instructions.add(index++, inst)
            inst.parent = this
            parent.slottracker.addValue(inst)
        }
    }

    fun remove(inst: Instruction) {
        if (inst.parent == this) {
            instructions.remove(inst)
            inst.parent = null
        }
    }

    fun replace(from: Instruction, to: Instruction){
        (0 until instructions.size).filter { instructions[it] == from }.forEach {
            instructions[it] = to
            to.parent = this
        }
    }

    fun isEmpty() = instructions.isEmpty()
    fun isNotEmpty() = !isEmpty()

    fun front() = instructions.first()
    fun back() = instructions.last()

    override fun toString() = print()

    abstract fun print(): String

    override fun iterator() = instructions.iterator()
    override fun hashCode() = defaultHashCode(name, parent)
    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is BasicBlock) return false
        return this.parent == other.parent && this.name == other.name
    }

    override fun getSuccSet() = successors.map { it as GraphNode }.toSet()
    override fun getPredSet() = predecessors.map { it as GraphNode }.toSet()
}

class BodyBlock(name: String, method: Method) : BasicBlock(BlockName(name), method) {
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

class CatchBlock(name: String, method: Method, val exception: Type) : BasicBlock(BlockName(name), method) {
    val throwers = mutableSetOf<List<BasicBlock>>()

    fun getEntries(): Set<BasicBlock> {
        val entries = mutableSetOf<BasicBlock>()
        val throwers = getAllThrowers()
        for (it in throwers) {
            for (pred in it.predecessors)
                if (!throwers.contains(pred)) entries.add(pred)
        }
        return entries
    }

    fun addThrowers(throwers: List<BasicBlock>) = this.throwers.add(throwers)
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
}