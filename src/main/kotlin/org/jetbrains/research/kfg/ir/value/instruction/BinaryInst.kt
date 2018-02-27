package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.Value

enum class BinaryOpcode {
    ADD,
    SUB,
    MUL,
    DIV,
    REM,
    SHL,
    SHR,
    USHR,
    AND,
    OR,
    XOR
}

fun BinaryOpcode.print(): String = when (this) {
    BinaryOpcode.ADD -> "+"
    BinaryOpcode.SUB -> "-"
    BinaryOpcode.MUL -> "*"
    BinaryOpcode.DIV -> "/"
    BinaryOpcode.REM -> "%"
    BinaryOpcode.SHL -> "<<"
    BinaryOpcode.SHR -> ">>"
    BinaryOpcode.USHR -> "u>>"
    BinaryOpcode.AND -> "&&"
    BinaryOpcode.OR -> "||"
    BinaryOpcode.XOR -> "^"
}

class BinaryInst(name: String, val opcode: BinaryOpcode, lhv: Value, rhv: Value)
    : Instruction(name, lhv.type, arrayOf(lhv, rhv)) {

    fun getLhv() = operands[0]
    fun getRhv() = operands[1]

    override fun print()= "$name = ${getLhv()} ${opcode.print()} ${getRhv()}"
}