package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.Value

class ArrayStoreInst(arrayRef: Value, index: Value, value: Value)
    : Instruction(UndefinedName, TF.getVoidType(), arrayOf(arrayRef, index, value)) {
    fun getArrayRef() = operands[0]
    fun getIndex() = operands[1]
    fun getValue() = operands[2]

    override fun print() = "${getArrayRef()}[${getIndex()}] = ${getValue()}"
}