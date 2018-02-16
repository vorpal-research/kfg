package org.jetbrains.research.kfg.value

import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.Type

class Argument(val argName: String, val method: Method, type: Type) : Value(type) {
    override fun getName() = argName
}