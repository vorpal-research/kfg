package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.Name

class CmpInst(name: Name, val opcode: CmpOpcode, lhv: Value, rhv: Value)
    : Instruction(name, TF.getIntType(), arrayOf(lhv, rhv)) {

    fun getLhv() = operands[0]
    fun getRhv() = operands[1]

    override fun print() = "$name = (${getLhv()} $opcode ${getRhv()})"
}