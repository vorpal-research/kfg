package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.Name
import org.jetbrains.research.kfg.ir.value.UsageContext
import org.jetbrains.research.kfg.type.Type

class CatchInst internal constructor(name: Name, type: Type, ctx: UsageContext) : Instruction(name, type, arrayOf(), ctx) {
    override fun print() = "$name = catch ${type.name}"
    override fun clone(ctx: UsageContext): Instruction = CatchInst(name.clone(), type, ctx)
}