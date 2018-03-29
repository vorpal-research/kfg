package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.Name
import org.jetbrains.research.kfg.type.Type

class CatchInst(name: Name, type: Type): Instruction(name, type, arrayOf()) {
    override fun print() = "$name = catch ${type.name}"
}