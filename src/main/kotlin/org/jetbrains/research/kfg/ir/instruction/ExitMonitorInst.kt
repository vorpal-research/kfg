package org.jetbrains.research.kfg.ir.instruction

import org.jetbrains.research.kfg.value.Value

class ExitMonitorInst(owner: Value) : Instruction(arrayOf(owner)) {
    fun getOwner() = operands[0]

    override fun print() = "exit monitor ${getOwner()}"
}