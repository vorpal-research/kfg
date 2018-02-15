package org.jetbrains.research.kfg.value

import org.jetbrains.research.kfg.type.Type

class CastValue(type: Type, obj: Value) : Value(type, arrayOf(obj)) {
    override fun getName() = "($type) ${operands[0]}"
}