package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.Name
import org.jetbrains.research.kfg.type.Type

class ArrayLoadInst(name: Name, type: Type, arrayRef: Value, index: Value)
    : Instruction(name, type, arrayOf(arrayRef, index)) {

    val arrayRef: Value
        get() = ops[0]

    val index: Value
        get() = ops[1]

    override fun print() = "$name = $arrayRef[$index]"
    override fun clone(): Instruction = ArrayLoadInst(name.clone(), type, arrayRef, index)
}