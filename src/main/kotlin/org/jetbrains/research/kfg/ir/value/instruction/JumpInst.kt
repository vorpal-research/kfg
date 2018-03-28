package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.TF

class JumpInst(successor: BasicBlock)
    : TerminateInst(UndefinedName, TF.getVoidType(), arrayOf(), arrayOf(successor)) {
    fun getSuccessor() = successors[0]

    override fun isTerminate() = true
    override fun print() = "goto ${getSuccessor().name}"
}