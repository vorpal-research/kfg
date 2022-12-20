package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.value.UndefinedName
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.type.ArrayType
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kthelper.assert.unreachable

class ArrayStoreInst internal constructor(
    arrayRef: Value,
    type: Type,
    index: Value,
    value: Value,
    ctx: UsageContext
) : Instruction(UndefinedName(), type, mutableListOf(arrayRef, index, value), ctx) {

    val arrayRef: Value
        get() = ops[0]

    val index: Value
        get() = ops[1]

    val value: Value
        get() = ops[2]

    val arrayComponent: Type
        get() = (arrayRef.type as? ArrayType)?.component ?: unreachable("Non-array ref in array store")

    override fun print() = "$arrayRef[$index] = $value"
    override fun clone(ctx: UsageContext): Instruction = ArrayStoreInst(arrayRef, type, index, value, ctx)
}
