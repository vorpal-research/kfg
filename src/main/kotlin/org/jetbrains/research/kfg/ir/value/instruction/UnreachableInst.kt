package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.type.Type

class UnreachableInst(type: Type) : TerminateInst(UndefinedName, type, arrayOf(), arrayOf()) {
    override fun print() = "unreachable"

    override fun clone() = this
}