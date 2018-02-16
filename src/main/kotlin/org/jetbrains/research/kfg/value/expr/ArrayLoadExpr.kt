package org.jetbrains.research.kfg.value.expr

import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.value.Value

class ArrayLoadExpr(type: Type, arrayRef: Value, index: Value)
    : Expr(type, arrayOf(arrayRef, index)) {
    override fun getName() = "${operands[0]}[${operands[1]}]"
}