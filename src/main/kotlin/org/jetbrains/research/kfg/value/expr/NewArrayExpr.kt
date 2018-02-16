package org.jetbrains.research.kfg.value.expr

import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.value.Value

class NewArrayExpr(type: Type, count: Value) : Expr(type, arrayOf(count)) {
    override fun getName() = "new $type[${operands[0]}]"
}