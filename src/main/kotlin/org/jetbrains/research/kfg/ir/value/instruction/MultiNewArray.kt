package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.ValueName
import org.jetbrains.research.kfg.type.Type

class MultiNewArray(name: ValueName, type: Type, val dims: Int): Instruction(name, type, arrayOf()) {
    override fun print() = "$name = new ${type.getName()}"
}