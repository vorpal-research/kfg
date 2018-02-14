package org.jetbrains.research.kfg.value

import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory

abstract class Constant(type: Type) : Value(type, arrayOf())

class ByteConstant(val value: Byte) : Constant(TypeFactory.instance.getByteType()) {
    override fun getName() = value.toString()
}
class ShortConstant(val value: Short) : Constant(TypeFactory.instance.getShortType()) {
    override fun getName() = value.toString()
}
class IntConstant(val value: Int) : Constant(TypeFactory.instance.getIntType()) {
    override fun getName() = value.toString()
}
class LongConstant(val value: Long) : Constant(TypeFactory.instance.getLongType()) {
    override fun getName() = value.toString()
}
class CharConstant(val value: Char) : Constant(TypeFactory.instance.getCharType()) {
    override fun getName() = value.toString()
}
class FloatConstant(val value: Float) : Constant(TypeFactory.instance.getFloatType()) {
    override fun getName() = value.toString()
}
class DoubleConstant(val value: Double) : Constant(TypeFactory.instance.getDoubleType()) {
    override fun getName() = value.toString()
}
class NullConstant : Constant(TypeFactory.instance.getNullType()) {
    companion object {
        val instance = NullConstant()
    }
    override fun getName() = "null"
}