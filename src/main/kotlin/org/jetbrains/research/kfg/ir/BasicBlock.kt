package org.jetbrains.research.kfg.ir

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
}