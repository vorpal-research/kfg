package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.TF

class JumpInst(successor: BasicBlock)
    : TerminateInst(UndefinedName, TF.getVoidType(), arrayOf(), arrayOf(successor)) {

    val successor: BasicBlock
        get() = succs[0]

    override fun print() = "goto ${successor.name}"
    override fun clone(): Instruction = JumpInst(successor)
}