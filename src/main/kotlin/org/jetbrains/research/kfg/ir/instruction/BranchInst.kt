package org.jetbrains.research.kfg.ir.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.value.Value

class BranchInst(cond: Value, val trueSuccessor: BasicBlock, val falseSuccessor: BasicBlock) : Instruction(arrayOf(cond)) {
    fun getCond() = operands[0]

    override fun isTerminate() = true
    override fun print() = "if (${getCond()}) goto ${trueSuccessor.name} else ${falseSuccessor.name}"
}