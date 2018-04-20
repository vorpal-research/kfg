package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.Name

class NewArrayInst(name: Name, val compType: Type, count: Value)
    : Instruction(name, TF.getArrayType(compType), arrayOf(count)) {

    fun getCount() = operands[0]

    override fun print() = "$name = new ${compType.name}[${getCount()}]"
    override fun clone(): Instruction = NewArrayInst(name.clone(), compType, getCount())
}