package org.jetbrains.research.kfg.value.expr

import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.value.Value

class NewExpr(type: Type) : Expr(type, arrayOf()) {
    override fun getName() = "new ${type.getName()}"
}