package org.jetbrains.research.kfg.value.expr

import org.jetbrains.research.kfg.InvalidOperandException
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.value.Value

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

class CmpExpr(val opcode: CmpOpcode, lhv: Value, rhv: Value) : Expr(TypeFactory.instance.getIntType(), arrayOf(lhv, rhv)) {
    override fun getName() = "${operands[0]} ${opcode.print()} ${operands[1]}"
}