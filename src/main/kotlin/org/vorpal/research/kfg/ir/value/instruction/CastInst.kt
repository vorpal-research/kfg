package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.value.Name
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.type.Type

class CastInst internal constructor(name: Name, type: Type, obj: Value, ctx: UsageContext) : Instruction(name, type, arrayOf(obj), ctx) {

    val operand: Value
        get() = ops[0]

    override fun print()= "$name = (${type.name}) $operand"
    override fun clone(ctx: UsageContext): Instruction = CastInst(name.clone(), type, operand, ctx)
}