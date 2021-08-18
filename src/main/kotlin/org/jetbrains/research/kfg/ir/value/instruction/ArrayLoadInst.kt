package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.Name
import org.jetbrains.research.kfg.ir.value.UsageContext
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type

class ArrayLoadInst internal constructor(
    name: Name,
    type: Type,
    arrayRef: Value,
    index: Value,
    ctx: UsageContext
) : Instruction(name, type, arrayOf(arrayRef, index), ctx) {

    val arrayRef: Value
        get() = ops[0]

    val index: Value
        get() = ops[1]

    override fun print() = "$name = $arrayRef[$index]"
    override fun clone(ctx: UsageContext): Instruction = ArrayLoadInst(name.clone(), type, arrayRef, index, ctx)
}