package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.Name
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.NullType

class ArrayLoadInst(name: Name, arrayRef: Value, index: Value)
    : Instruction(name, if (arrayRef.type is NullType) TF.getNullType() else (arrayRef.type as ArrayType).component, arrayOf(arrayRef, index)) {

    fun getArrayRef() = operands[0]
    fun getIndex() = operands[1]

    override fun print() = "$name = ${getArrayRef()}[${getIndex()}]"

    override fun clone(): Instruction = ArrayLoadInst(name.clone(), getArrayRef(), getIndex())
}