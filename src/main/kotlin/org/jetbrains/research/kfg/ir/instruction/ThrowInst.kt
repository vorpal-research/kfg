package org.jetbrains.research.kfg.ir.instruction

import org.jetbrains.research.kfg.value.Value

class ThrowInst(exc: Value) : Instruction(arrayOf(exc)) {
    fun getThrowable() = operands[0]

    override fun print() = "throw ${getThrowable()}"
}