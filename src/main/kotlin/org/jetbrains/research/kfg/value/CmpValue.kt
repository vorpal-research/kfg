package org.jetbrains.research.kfg.value

import org.jetbrains.research.kfg.InvalidOperandException
import org.jetbrains.research.kfg.type.TypeFactory

enum class CmpOpcode {
    EQ,
    LT,
    GT,
    LE,
    GE,
    CMPG,
    CMPL
}

class CmpValue(val opcode: CmpOpcode, lhv: Value, rhv: Value) : Value(TypeFactory.instance.getIntType(), arrayOf(lhv, rhv)) {
    init {
        if (!lhv.type.equals(rhv.type)) throw InvalidOperandException("Cmp value with different types: ${lhv.type} ${rhv.type}")
    }
    override fun getName() = "${operands[0]} ${opcode.name} ${operands[1]}"
}