package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.type.Type

class JumpInst(type: Type, successor: BasicBlock)
    : TerminateInst(UndefinedName(), type, arrayOf(), arrayOf(successor)) {

    val successor: BasicBlock
        get() = succs[0]

    override fun print() = "goto ${successor.name}"
    override fun clone(): Instruction = JumpInst(type, successor)
}