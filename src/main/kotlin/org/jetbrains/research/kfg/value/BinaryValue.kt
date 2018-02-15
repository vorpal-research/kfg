package org.jetbrains.research.kfg.value

import org.jetbrains.research.kfg.InvalidOperandException

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

class BinaryValue(val opcode: BinaryOpcode, lhv: Value, rhv: Value) : Value(lhv.type, arrayOf(lhv, rhv)) {
    init {
        if (!lhv.type.equals(rhv.type)) throw InvalidOperandException("Binary value with different types: ${lhv.type} ${rhv.type}")
    }

    override fun getName() = "${operands[0]} ${opcode.name} ${operands[1]}"
}