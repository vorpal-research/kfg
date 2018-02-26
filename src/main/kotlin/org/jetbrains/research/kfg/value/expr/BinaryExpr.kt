package org.jetbrains.research.kfg.value.expr

import org.jetbrains.research.kfg.InvalidOperandException
import org.jetbrains.research.kfg.value.Value

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

class BinaryExpr(val opcode: BinaryOpcode, lhv: Value, rhv: Value) : Expr(lhv.type, arrayOf(lhv, rhv)) {
    override fun getName() = "${operands[0]} ${opcode.print()} ${operands[1]}"
}