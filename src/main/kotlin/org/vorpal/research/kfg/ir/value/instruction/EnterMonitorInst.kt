package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.value.UndefinedName
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.type.Type

@Suppress("MemberVisibilityCanBePrivate")
class EnterMonitorInst internal constructor(
    type: Type,
    owner: Value,
    ctx: UsageContext
) : Instruction(UndefinedName(), type, mutableListOf(owner), ctx) {

    val owner: Value
        get() = ops[0]

    override fun print() = "enter monitor $owner"
    override fun clone(ctx: UsageContext): Instruction = EnterMonitorInst(type, owner, ctx)
}
