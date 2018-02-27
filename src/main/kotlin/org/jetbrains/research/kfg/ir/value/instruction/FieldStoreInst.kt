package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.TypeFactory

class FieldStoreInst(field: Value, value: Value)
    : Instruction(UndefinedName.instance, TypeFactory.instance.getVoidType(), arrayOf(field, value)) {

    fun getField() = operands[0]
    fun getValue() = operands[1]

    override fun print() = "${getField()} = ${getValue()}"
}