package org.jetbrains.research.kfg.value

import org.jetbrains.research.kfg.type.TypeFactory

class InstanceOfValue(obj: Value) : Value(TypeFactory.instance.getIntType(), arrayOf(obj)) {
    override fun getName() = "${operands[0]} instanceOf $type"
}