package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.ir.instruction.Instruction

class BasicBlock {
    val name: String
    val method: Method
    val predecessors = mutableListOf<BasicBlock>()
    val successors = mutableListOf<BasicBlock>()
    val instructions = mutableListOf<Instruction>()

    constructor(name: String, method: Method) {
        this.name = name
        this.method = method
    }

    fun addSuccessor(bb: BasicBlock) = successors.add(bb)
    fun addPredecessor(bb: BasicBlock) = predecessors.add(bb)
}