package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.value.Name
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.type.Type

class CatchInst internal constructor(name: Name, type: Type, ctx: UsageContext) : Instruction(name, type, arrayOf(), ctx) {
    override fun print() = "$name = catch ${type.name}"
    override fun clone(ctx: UsageContext): Instruction = CatchInst(name.clone(), type, ctx)
}