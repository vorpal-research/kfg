package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.ValueName
import org.jetbrains.research.kfg.type.Type

class NewInst(name: ValueName, type: Type) : Instruction(name, type, arrayOf()) {
    override fun print() = "$name = new ${type.name}"
}