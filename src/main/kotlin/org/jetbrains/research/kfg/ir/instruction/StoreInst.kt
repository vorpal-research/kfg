package org.jetbrains.research.kfg.ir.instruction

import org.jetbrains.research.kfg.value.Value

class StoreInst(arrayRef: Value, index: Value, value: Value) : Instruction(arrayOf(arrayRef, index, value)) {
    fun getArrayRef() = operands[0]
    fun getIndex() = operands[1]
    fun getValue() = operands[2]

    override fun print() = "${getArrayRef()}[${getIndex()}] = ${getValue()}"
}