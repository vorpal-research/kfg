package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.ir.value.Value

class BranchInst(cond: Value, val trueSuccessor: BasicBlock, val falseSuccessor: BasicBlock)
    : Instruction("", TypeFactory.instance.getVoidType(), arrayOf(cond)) {
    fun getCond() = operands[0]

    override fun isTerminate() = true
    override fun print() = "if (${getCond()}) goto ${trueSuccessor.name} else ${falseSuccessor.name}"
}