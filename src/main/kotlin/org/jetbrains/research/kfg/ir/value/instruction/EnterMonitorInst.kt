package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.ir.value.Value

class EnterMonitorInst(owner: Value)
    : Instruction("", TypeFactory.instance.getVoidType(), arrayOf(owner)) {
    fun getOwner() = operands[0]

    override fun print() = "enter monitor ${getOwner()}"
}