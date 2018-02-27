package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type

class InstanceOfInst(name: String, val targetType: Type, obj: Value)
    : Instruction(name, TypeFactory.instance.getIntType(), arrayOf(obj)) {

    fun getOperand() = operands[0]

    override fun print() = "$name = ${getOperand()} instanceOf ${targetType.getName()}"
}