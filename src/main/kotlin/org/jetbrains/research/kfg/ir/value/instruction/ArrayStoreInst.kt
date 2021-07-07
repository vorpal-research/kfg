package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.UsageContext
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log

class ArrayStoreInst internal constructor(
    arrayRef: Value,
    type: Type,
    index: Value,
    value: Value,
    ctx: UsageContext
) :    Instruction(UndefinedName(), type, arrayOf(arrayRef, index, value), ctx) {

    val arrayRef: Value
        get() = ops[0]

    val index: Value
        get() = ops[1]

    val value: Value
        get() = ops[2]

    val arrayComponent: Type
        get() = (arrayRef.type as? ArrayType)?.component ?: unreachable { log.error("Non-array ref in array store") }

    override fun print() = "$arrayRef[$index] = $value"
    override fun clone(ctx: UsageContext): Instruction = ArrayStoreInst(arrayRef, type, index, value, ctx)
}