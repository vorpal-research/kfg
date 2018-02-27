package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.ir.value.Value

class ThrowInst(exc: Value) : Instruction("", TypeFactory.instance.getVoidType(), arrayOf(exc)) {
    fun getThrowable() = operands[0]

    override fun print() = "throw ${getThrowable()}"
}