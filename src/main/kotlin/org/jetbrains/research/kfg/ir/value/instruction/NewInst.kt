package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.Name
import org.jetbrains.research.kfg.type.Type

class NewInst(name: Name, type: Type) : Instruction(name, type, arrayOf()) {
    override fun print() = "$name = new ${type.name}"
    override fun clone(): Instruction = NewInst(name.clone(), type)
}