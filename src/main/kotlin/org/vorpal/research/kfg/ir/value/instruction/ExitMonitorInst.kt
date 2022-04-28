package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.value.UndefinedName
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.type.Type

class ExitMonitorInst internal constructor(type: Type, owner: Value, ctx: UsageContext) :
    Instruction(UndefinedName(), type, arrayOf(owner), ctx) {

    val owner: Value
        get() = ops[0]

    override fun print() = "exit monitor $owner"
    override fun clone(ctx: UsageContext): Instruction = ExitMonitorInst(type, owner, ctx)
}