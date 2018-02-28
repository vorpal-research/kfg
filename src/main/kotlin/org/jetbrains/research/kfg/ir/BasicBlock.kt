package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory
import java.rmi.UnexpectedException

abstract class BasicBlock(val name: String, val method: Method): Iterable<Instruction> {
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
        inst.bb = this
    }

    fun remove(inst: Instruction) {
        instructions.remove(inst)
    }

    fun replace(from: Instruction, to: Instruction){
        (0 until instructions.size).filter { instructions[it] == from }.forEach {
            instructions[it] = to
            to.bb = this
        }
    }

    fun isEmpty() = instructions.isEmpty()
    fun isNotEmpty() = !isEmpty()

    fun front() = instructions.first()
    fun back() = instructions.last()

    override fun toString() = print()

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is BasicBlock) return false
        return this.method == other.method && this.name == other.name
    }

    abstract fun print(): String

    override fun iterator() = instructions.iterator()
}

class BodyBlock(name: String, method: Method) : BasicBlock(name, method) {
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

class CatchBlock(name: String, method: Method, val exception: Type) : BasicBlock(name, method) {
    val throwers = mutableListOf<BasicBlock>()

    fun addThrower(bb: BasicBlock) = throwers.add(bb)
    fun addThrowers(vararg blocks: BasicBlock) = throwers.addAll(blocks)

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
        val defaultException = TypeFactory.instance.getRefType("java/lang/Throwable")
    }
}