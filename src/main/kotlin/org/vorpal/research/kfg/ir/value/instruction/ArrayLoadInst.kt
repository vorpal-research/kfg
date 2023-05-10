package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.value.Name
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.type.Type

class ArrayLoadInst internal constructor(
    name: Name,
    type: Type,
    arrayRef: Value,
    index: Value,
    ctx: UsageContext
) : Instruction(name, type, mutableListOf(arrayRef, index), ctx) {

    @Suppress("MemberVisibilityCanBePrivate")
    val arrayRef: Value
        get() = ops[0]

    val index: Value
        get() = ops[1]

    override fun print() = "$name = $arrayRef[$index]"
    override fun clone(ctx: UsageContext): Instruction = ArrayLoadInst(name.clone(), type, arrayRef, index, ctx)
}
