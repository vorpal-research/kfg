package org.jetbrains.research.kfg.ir.instruction

import org.jetbrains.research.kfg.value.Value

class AssignInst(lhv: Value, rhv: Value) : Instruction(arrayOf(lhv, rhv)) {
    fun getLhv() = operands[0]
    fun getRhv() = operands[1]

    override fun print(): String = "${getLhv()} = ${getRhv()}"
}