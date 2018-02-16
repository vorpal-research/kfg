package org.jetbrains.research.kfg.value

import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.type.parseDesc

abstract class Constant(type: Type) : Value(type)

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

class StringConstant(val value: String) : Constant(TypeFactory.instance.getRefType("java.lang.String")) {
    override fun getName() = value
}

class ClassConstant(val desc: String) : Constant(parseDesc(desc)) {
    override fun getName() = desc
}

class MethodConstant(val method: Method) : Constant(TypeFactory.instance.getRefType("java.lang.invoke.MethodHandle")) {
    override fun getName() = "${method.name}.handle"
}