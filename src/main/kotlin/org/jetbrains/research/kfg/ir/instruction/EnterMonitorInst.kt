package org.jetbrains.research.kfg.ir.instruction

import org.jetbrains.research.kfg.value.Value

class EnterMonitorInst(owner: Value) : Instruction(arrayOf(owner)) {
    fun getOwner() = operands[0]

    override fun print() = "enter monitor ${getOwner()}"
}