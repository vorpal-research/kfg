package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.Name
import org.jetbrains.research.kfg.ir.value.UsageContext
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type

class CastInst internal constructor(name: Name, type: Type, obj: Value, ctx: UsageContext) : Instruction(name, type, arrayOf(obj), ctx) {

    val operand: Value
        get() = ops[0]

    override fun print()= "$name = (${type.name}) $operand"
    override fun clone(ctx: UsageContext): Instruction = CastInst(name.clone(), type, operand, ctx)
}