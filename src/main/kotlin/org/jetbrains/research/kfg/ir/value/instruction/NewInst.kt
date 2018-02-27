package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.type.Type

class NewInst(name: String, type: Type) : Instruction(name, type, arrayOf()) {
    override fun print() = "$name = new ${type.getName()}"
}