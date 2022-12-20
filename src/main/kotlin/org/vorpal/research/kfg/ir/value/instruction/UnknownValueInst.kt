package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.value.Name
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.type.Type

class UnknownValueInst internal constructor(name: Name, type: Type, ctx: UsageContext) :
    Instruction(name, type, mutableListOf(), ctx) {
    override fun clone(ctx: UsageContext): Instruction = UnknownValueInst(name.clone(), type, ctx)

    override fun print(): String = "$name = unknown<$type>"
}
