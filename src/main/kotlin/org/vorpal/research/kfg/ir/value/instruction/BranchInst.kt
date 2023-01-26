package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.value.UndefinedName
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.type.Type

class BranchInst internal constructor(
    cond: Value,
    type: Type,
    trueSuccessor: BasicBlock,
    falseSuccessor: BasicBlock,
    ctx: UsageContext
) : TerminateInst(UndefinedName(), type, mutableListOf(cond), mutableListOf(trueSuccessor, falseSuccessor), ctx) {

    val cond: Value
        get() = ops[0]

    val trueSuccessor: BasicBlock
        get() = internalSuccessors[0]

    val falseSuccessor: BasicBlock
        get() = internalSuccessors[1]

    override fun print() = "if ($cond) goto ${trueSuccessor.name} else ${falseSuccessor.name}"
    override fun clone(ctx: UsageContext): Instruction = BranchInst(cond, type, trueSuccessor, falseSuccessor, ctx)
}
