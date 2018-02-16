package org.jetbrains.research.kfg.value.expr

import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.value.Value

class InstanceOfExpr(obj: Value) : Expr(TypeFactory.instance.getIntType(), arrayOf(obj)) {
    override fun getName() = "${operands[0]} instanceOf $type"
}