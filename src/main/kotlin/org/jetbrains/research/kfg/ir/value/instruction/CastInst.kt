package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.ir.value.Value

class CastInst(name: String, type: Type, obj: Value) : Instruction(name, type, arrayOf(obj)) {
    fun getOperand() = operands[0]

    override fun print()= "$name = (${type.getName()}) ${getOperand()}"
}