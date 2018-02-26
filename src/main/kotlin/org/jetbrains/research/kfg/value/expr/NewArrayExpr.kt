package org.jetbrains.research.kfg.value.expr

import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.value.Value

class NewArrayExpr(val compType: Type, count: Value) : Expr(TypeFactory.instance.getArrayType(compType), arrayOf(count)) {
    override fun getName() = "new ${compType.getName()}[${operands[0]}]"
}