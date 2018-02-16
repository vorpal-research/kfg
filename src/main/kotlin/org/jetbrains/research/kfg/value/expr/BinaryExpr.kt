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

class BinaryExpr(val opcode: BinaryOpcode, lhv: Value, rhv: Value) : Expr(lhv.type, arrayOf(lhv, rhv)) {
    init {
        if (!lhv.type.equals(rhv.type)) throw InvalidOperandException("Binary value with different types: ${lhv.type} ${rhv.type}")
    }

    override fun getName() = "${operands[0]} ${opcode.name} ${operands[1]}"
}