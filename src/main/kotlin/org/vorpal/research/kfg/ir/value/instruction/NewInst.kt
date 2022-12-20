package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.value.Name
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.type.Type

class NewInst internal constructor(name: Name, type: Type, ctx: UsageContext) :
    Instruction(name, type, mutableListOf(), ctx) {
    override fun print() = "$name = new ${type.name}"
    override fun clone(ctx: UsageContext): Instruction = NewInst(name.clone(), type, ctx)
}
