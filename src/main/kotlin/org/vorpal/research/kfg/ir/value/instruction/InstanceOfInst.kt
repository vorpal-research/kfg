package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.value.Name
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.type.Type

class InstanceOfInst internal constructor(
    name: Name,
    type: Type,
    val targetType: Type,
    obj: Value,
    ctx: UsageContext
) : Instruction(name, type, mutableListOf(obj), ctx) {

    val operand: Value
        get() = ops[0]

    override fun print() = "$name = $operand instanceOf ${targetType.name}"
    override fun clone(ctx: UsageContext): Instruction = InstanceOfInst(name.clone(), type, targetType, operand, ctx)
}
