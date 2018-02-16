package org.jetbrains.research.kfg.value.expr

import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.value.Value

class CheckCastExpr(type: Type, obj: Value) : Expr(type, arrayOf(obj)) {
    override fun getName() = "($type) ${operands[0]}"
}