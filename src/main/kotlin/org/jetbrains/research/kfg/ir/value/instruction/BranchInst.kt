package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.UsageContext
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type

class BranchInst internal constructor(
    cond: Value,
    type: Type,
    trueSuccessor: BasicBlock,
    falseSuccessor: BasicBlock,
    ctx: UsageContext
) : TerminateInst(UndefinedName(), type, arrayOf(cond), arrayOf(trueSuccessor, falseSuccessor), ctx) {

    val cond: Value
        get() = ops[0]

    val trueSuccessor: BasicBlock
        get() = succs[0]

    val falseSuccessor: BasicBlock
        get() = succs[1]

    override fun print() = "if ($cond) goto ${trueSuccessor.name} else ${falseSuccessor.name}"
    override fun clone(ctx: UsageContext): Instruction = BranchInst(cond, type, trueSuccessor, falseSuccessor, ctx)
}