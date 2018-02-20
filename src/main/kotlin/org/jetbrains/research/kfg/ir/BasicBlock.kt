package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.ir.instruction.Instruction
import org.jetbrains.research.kfg.type.Type

class BasicBlock {
    val name: String
    val method: Method
    val predecessors = mutableSetOf<BasicBlock>()
    val successors = mutableSetOf<BasicBlock>()
    val instructions = mutableListOf<Instruction>()
    val exceptionHandlers = mutableMapOf<Type, BasicBlock>()

    constructor(name: String, method: Method) {
        this.name = name
        this.method = method
    }

    fun addSuccessor(bb: BasicBlock) = successors.add(bb)
    fun addPredecessor(bb: BasicBlock) = predecessors.add(bb)
    fun addHandler(exc: Type, bb: BasicBlock) {
        exceptionHandlers[exc] = bb
    }
    fun addInstruction(inst: Instruction) = instructions.add(inst)

    fun isEmpty() = instructions.isEmpty()
    fun isNotEmpty() = !isEmpty()

    fun front() = instructions.first()
    fun back() = instructions.last()

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendln("$name:")
        instructions.forEach {
            sb.appendln("\t$it")
        }
        return sb.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) return false
        if (other !is BasicBlock) return false
        return this.method == other.method && this.name == other.name
    }
}