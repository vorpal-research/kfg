package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.ir.value.Value

enum class CmpOpcode {
    EQ,
    NE,
    LT,
    GT,
    LE,
    GE,
    CMPG,
    CMPL
}

fun CmpOpcode.print(): String = when (this) {
    CmpOpcode.EQ -> "=="
    CmpOpcode.NE -> "!="
    CmpOpcode.LT -> "<"
    CmpOpcode.GT -> ">"
    CmpOpcode.LE -> "<="
    CmpOpcode.GE -> ">="
    CmpOpcode.CMPG -> "cmpg"
    CmpOpcode.CMPL -> "cmpl"
}

class CmpInst(name: String, val opcode: CmpOpcode, lhv: Value, rhv: Value)
    : Instruction(name, TypeFactory.instance.getIntType(), arrayOf(lhv, rhv)) {

    fun getLhv() = operands[0]
    fun getRhv() = operands[1]

    override fun print() = "$name = (${getLhv()} ${opcode.print()} ${getRhv()})"
}