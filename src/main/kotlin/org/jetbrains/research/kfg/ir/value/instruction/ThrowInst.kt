package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type

class ThrowInst(type: Type, exc: Value) : TerminateInst(UndefinedName, type, arrayOf(exc), arrayOf()) {

    val throwable: Value
        get() = ops[0]

    override fun print() = "throw $throwable"
    override fun clone(): Instruction = ThrowInst(type, throwable)
}