package org.jetbrains.research.kfg.value

import org.jetbrains.research.kfg.type.Type

class NewArrayValue(type: Type, count: Value) : Value(type, arrayOf(count)) {
    override fun getName() = "new $type[${operands[0]}]"
}