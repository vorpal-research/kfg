package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.value.UndefinedName
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.type.Type

class JumpInst internal constructor(
    type: Type,
    successor: BasicBlock,
    ctx: UsageContext
) : TerminateInst(UndefinedName(), type, mutableListOf(), mutableListOf(successor), ctx) {

    val successor: BasicBlock
        get() = succs[0]

    override fun print() = "goto ${successor.name}"
    override fun clone(ctx: UsageContext): Instruction = JumpInst(type, successor, ctx)
}
