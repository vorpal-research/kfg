package org.jetbrains.research.kfg.ir.instruction

import org.jetbrains.research.kfg.ir.BasicBlock

class JumpInst(val successor: BasicBlock) : Instruction(arrayOf()) {
    override fun isTerminate() = true
    override fun print() = "goto ${successor.name}"
}