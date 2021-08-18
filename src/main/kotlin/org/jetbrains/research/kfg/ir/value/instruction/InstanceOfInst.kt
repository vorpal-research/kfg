package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.Name
import org.jetbrains.research.kfg.ir.value.UsageContext
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type

class InstanceOfInst internal constructor(name: Name, type: Type, val targetType: Type, obj: Value, ctx: UsageContext) :
    Instruction(name, type, arrayOf(obj), ctx) {

    val operand: Value
        get() = ops[0]

    override fun print() = "$name = $operand instanceOf ${targetType.name}"
    override fun clone(ctx: UsageContext): Instruction = InstanceOfInst(name.clone(), type, targetType, operand, ctx)
}