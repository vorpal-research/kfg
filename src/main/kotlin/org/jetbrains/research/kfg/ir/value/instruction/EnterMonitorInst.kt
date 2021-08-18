package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.UsageContext
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type

class EnterMonitorInst internal constructor(
    type: Type,
    owner: Value,
    ctx: UsageContext
) : Instruction(UndefinedName(), type, arrayOf(owner), ctx) {

    val owner: Value
        get() = ops[0]

    override fun print() = "enter monitor $owner"
    override fun clone(ctx: UsageContext): Instruction = EnterMonitorInst(type, owner, ctx)
}