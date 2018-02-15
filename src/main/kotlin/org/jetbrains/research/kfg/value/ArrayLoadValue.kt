package org.jetbrains.research.kfg.value

import org.jetbrains.research.kfg.type.Type

class ArrayLoadValue(type: Type, arrayRef: Value, index: Value)
    : Value(type, arrayOf(arrayRef, index)) {
    override fun getName() = "${operands[0]}[${operands[1]}]"
}