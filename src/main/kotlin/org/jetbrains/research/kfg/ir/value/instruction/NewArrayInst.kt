package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.ValueName

class NewArrayInst(name: ValueName, val compType: Type, count: Value)
    : Instruction(name, TypeFactory.instance.getArrayType(compType), arrayOf(count)) {

    fun getCount() = operands[0]

    override fun print() = "$name = new ${compType.getName()}[${getCount()}]"
}