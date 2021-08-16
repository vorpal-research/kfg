package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kthelper.assert.unreachable

class ArrayStoreInst(arrayRef: Value, type: Type, index: Value, value: Value)
    : Instruction(UndefinedName(), type, arrayOf(arrayRef, index, value)) {

    val arrayRef: Value
        get() = ops[0]

    val index: Value
        get() = ops[1]

    val value: Value
        get() = ops[2]

    val arrayComponent: Type
        get() = (arrayRef.type as? ArrayType)?.component ?: unreachable("Non-array ref in array store")

    override fun print() = "$arrayRef[$index] = $value"
    override fun clone(): Instruction = ArrayStoreInst(arrayRef, type, index, value)
}