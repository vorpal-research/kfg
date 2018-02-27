package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.ValueName

class FieldLoadInst(name: ValueName, field: Value): Instruction(name, field.type, arrayOf(field)) {
    fun getField() = operands[0]
    override fun print() = "$name = ${getField()}"
}