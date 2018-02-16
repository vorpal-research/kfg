package org.jetbrains.research.kfg.value.expr

import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.value.Value

enum class UnaryOpcode {
    NEG,
    LENGTH
}

class UnaryExpr(val opcode: UnaryOpcode, arrayRef: Value) : Expr(TypeFactory.instance.getIntType(), arrayOf(arrayRef)) {
    override fun getName() = if (opcode == UnaryOpcode.LENGTH) "${operands[0]}.length" else "-${operands[0]}"
}