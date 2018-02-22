package org.jetbrains.research.kfg.value.expr

import org.jetbrains.research.kfg.type.Type

class CatchExpr(type: Type): Expr(type, arrayOf()) {
    override fun getName() = "catch ${type.getName()}"
}