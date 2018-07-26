package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.Value

class ThrowInst(exc: Value) : TerminateInst(UndefinedName, TF.getVoidType(), arrayOf(exc), arrayOf()) {

    val throwable: Value
        get() = ops[0]

    override fun print() = "throw $throwable"
    override fun clone(): Instruction = ThrowInst(throwable)
}