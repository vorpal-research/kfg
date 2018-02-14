package org.jetbrains.research.kfg.value

import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory

class ArrayLoadValue(type: Type, arrayRef: Value, index: Value)
    : Value(type, arrayOf(arrayRef, index)) {
    override fun getName() = "${operands[0]}[${operands[1]}]"
}

class NewArrayValue(type: Type, count: Value) : Value(type, arrayOf(count)) {
    override fun getName() = "new $type[${operands[0]}]"
}

class NewValue(type: Type) : Value(type, arrayOf()) {
    override fun getName() = "new $type"
}

class CheckCastValue(type: Type, obj: Value) : Value(type, arrayOf(obj)) {
    override fun getName() = "($type) ${operands[0]}"
}

class InstanceOfValue(obj: Value) : Value(TypeFactory.instance.getIntType(), arrayOf(obj)) {
    override fun getName() = "${operands[0]} instanceOf $type"
}