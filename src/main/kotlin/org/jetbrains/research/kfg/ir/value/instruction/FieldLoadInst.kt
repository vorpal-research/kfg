package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.Value

class FieldLoadInst(name: String, field: Value): Instruction(name, field.type, arrayOf(field)) {
    fun getField() = operands[0]
    override fun print() = "$name = ${getField()}"
}