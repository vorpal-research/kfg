package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.Value

class BranchInst(cond: Value, trueSuccessor: BasicBlock, falseSuccessor: BasicBlock)
    : TerminateInst(UndefinedName, TF.getVoidType(), arrayOf(cond), arrayOf(trueSuccessor, falseSuccessor)) {
    fun getCond() = operands[0]
    fun getTrueSuccessor() = successors[0]
    fun getFalseSuccessor() = successors[1]

    override fun isTerminate() = true
    override fun print() = "if (${getCond()}) goto ${getTrueSuccessor().name} else ${getFalseSuccessor().name}"
    override fun clone(): Instruction = BranchInst(getCond(), getTrueSuccessor(), getFalseSuccessor())
}