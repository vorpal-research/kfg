package org.jetbrains.research.kfg.value

import org.jetbrains.research.kfg.type.TypeFactory

class ArrayLengthValue(arrayRef: Value) : Value(TypeFactory.instance.getIntType(), arrayOf(arrayRef)) {
    override fun getName() = "${operands[0]}.length"
}