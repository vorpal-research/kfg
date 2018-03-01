package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.ValueName
import org.jetbrains.research.kfg.type.ArrayType

class ArrayLoadInst(name: ValueName, arrayRef: Value, index: Value)
    : Instruction(name, (arrayRef.type as ArrayType).component, arrayOf(arrayRef, index)) {

    fun getArrayRef() = operands[0]
    fun getIndex() = operands[1]

    override fun print() = "$name = ${getArrayRef()}[${getIndex()}]"
}