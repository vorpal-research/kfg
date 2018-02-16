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

class CmpExpr(val opcode: CmpOpcode, lhv: Value, rhv: Value) : Expr(TypeFactory.instance.getIntType(), arrayOf(lhv, rhv)) {
    init {
        if (!lhv.type.equals(rhv.type)) throw InvalidOperandException("Cmp value with different types: ${lhv.type} ${rhv.type}")
    }
    override fun getName() = "${operands[0]} ${opcode.name} ${operands[1]}"
}