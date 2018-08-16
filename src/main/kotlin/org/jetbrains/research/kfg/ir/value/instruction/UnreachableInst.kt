package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.UndefinedName

class UnreachableInst : TerminateInst(UndefinedName, TF.voidType, arrayOf(), arrayOf()) {
    override fun print() = "unreachable"

    override fun clone() = this
}