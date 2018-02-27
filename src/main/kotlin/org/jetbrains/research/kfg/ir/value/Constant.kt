package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.type.parseDesc

abstract class Constant(name: String, type: Type) : Value(name, type)

class BoolConstant(val value: Boolean) : Constant(value.toString(), TypeFactory.instance.getBoolType())
class ByteConstant(val value: Byte) : Constant(value.toString(), TypeFactory.instance.getByteType())
class ShortConstant(val value: Short) : Constant(value.toString(), TypeFactory.instance.getShortType())
class IntConstant(val value: Int) : Constant(value.toString(), TypeFactory.instance.getIntType())
class LongConstant(val value: Long) : Constant(value.toString(), TypeFactory.instance.getLongType())
class CharConstant(val value: Char) : Constant(value.toString(), TypeFactory.instance.getCharType())
class FloatConstant(val value: Float) : Constant(value.toString(), TypeFactory.instance.getFloatType())
class DoubleConstant(val value: Double) : Constant(value.toString(), TypeFactory.instance.getDoubleType())

class ClassConstant(val desc: String) : Constant(desc, parseDesc(desc))
class StringConstant(val value: String) : Constant("\"$value\"", TypeFactory.instance.getRefType("java.lang.String"))
class MethodConstant(val method: Method) : Constant(method.name, TypeFactory.instance.getRefType("java.lang.invoke.MethodHandle"))

class NullConstant : Constant("null", TypeFactory.instance.getNullType()) {
    companion object {
        val instance = NullConstant()
    }
}