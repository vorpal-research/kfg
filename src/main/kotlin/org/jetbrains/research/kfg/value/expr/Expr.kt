package org.jetbrains.research.kfg.value.expr

import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.value.Value

abstract class Expr(type: Type, val operands: Array<Value>) : Value(type)