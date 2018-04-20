package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.Name
import org.jetbrains.research.kfg.type.Type

class MultiNewArrayInst(name: Name, type: Type, val dims: Int): Instruction(name, type, arrayOf()) {
    override fun print() = "$name = new ${type.name}"
    override fun clone(): Instruction = MultiNewArrayInst(name.clone(), type, dims)
}