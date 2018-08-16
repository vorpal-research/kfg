package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.Value

class ArrayStoreInst(arrayRef: Value, index: Value, value: Value)
    : Instruction(UndefinedName, TF.voidType, arrayOf(arrayRef, index, value)) {

    val arrayRef: Value
        get() = ops[0]

    val index: Value
        get() = ops[1]

    val value: Value
        get() = ops[2]

    override fun print() = "$arrayRef[$index] = $value"
    override fun clone(): Instruction = ArrayStoreInst(arrayRef, index, value)
}