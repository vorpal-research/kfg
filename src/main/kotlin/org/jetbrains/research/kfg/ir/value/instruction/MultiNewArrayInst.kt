package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.Name
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type

class MultiNewArrayInst(name: Name, type: Type, dimentions: Array<Value>): Instruction(name, type, dimentions) {
    fun getDimensions() = operands
    fun numDimensions() = operands.size

    override fun print() = "$name = new ${type.name}"
    override fun clone(): Instruction = MultiNewArrayInst(name.clone(), type, operands)
}