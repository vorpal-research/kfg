package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.Name

class CmpInst(name: Name, type: Type, val opcode: CmpOpcode, lhv: Value, rhv: Value)
    : Instruction(name, type, arrayOf(lhv, rhv)) {

    fun getLhv() = operands[0]
    fun getRhv() = operands[1]

    override fun print() = "$name = (${getLhv()} $opcode ${getRhv()})"
    override fun clone(): Instruction = CmpInst(name.clone(), type, opcode, getLhv(), getRhv())
}