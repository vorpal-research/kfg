package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.Value

class EnterMonitorInst(owner: Value)
    : Instruction(UndefinedName, TF.getVoidType(), arrayOf(owner)) {
    fun getOwner() = operands[0]

    override fun print() = "enter monitor ${getOwner()}"
    override fun clone(): Instruction = EnterMonitorInst(getOwner())
}