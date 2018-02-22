package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.ir.instruction.Instruction
import org.jetbrains.research.kfg.type.Type

class BasicBlock {
    val name: String
    val method: Method
    var isExceptionHandler = false
    val predecessors = mutableSetOf<BasicBlock>()
    val successors = mutableSetOf<BasicBlock>()
    val instructions = mutableListOf<Instruction>()
    val exceptionHandlers = mutableMapOf<BasicBlock, Type>()

    constructor(name: String, method: Method) {
        this.name = name
        this.method = method
    }

    fun addSuccessor(bb: BasicBlock) = successors.add(bb)
    fun addSuccessors(vararg bbs: BasicBlock) = successors.addAll(bbs)
    fun addPredecessor(bb: BasicBlock) = predecessors.add(bb)
    fun addPredecessors(vararg bbs: BasicBlock) = predecessors.addAll(bbs)
    fun addHandler(bb: BasicBlock, exc: Type) {
        exceptionHandlers[bb] = exc
    }

    fun addInstruction(inst: Instruction){
        instructions.add(inst)
        inst.bb = this
    }

    fun isEmpty() = instructions.isEmpty()
    fun isNotEmpty() = !isEmpty()

    fun front() = instructions.first()
    fun back() = instructions.last()

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("$name: \t")
        predecessors.take(1).forEach { sb.append(it.name) }
        predecessors.drop(1).forEach { sb.append(", ${it.name}") }
        sb.appendln()
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