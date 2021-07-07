package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.Name
import org.jetbrains.research.kfg.ir.value.UsageContext
import org.jetbrains.research.kfg.type.Type

class NewInst internal constructor(name: Name, type: Type, ctx: UsageContext) :
    Instruction(name, type, arrayOf(), ctx) {
    override fun print() = "$name = new ${type.name}"
    override fun clone(ctx: UsageContext): Instruction = NewInst(name.clone(), type, ctx)
}