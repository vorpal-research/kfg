package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.Name

class BinaryInst(name: Name, val opcode: BinaryOpcode, lhv: Value, rhv: Value)
    : Instruction(name, lhv.type, arrayOf(lhv, rhv)) {

    fun getLhv() = operands[0]
    fun getRhv() = operands[1]

    override fun print()= "$name = ${getLhv()} $opcode ${getRhv()}"
}