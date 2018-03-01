package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.parseDesc

abstract class Constant(name: String, type: Type) : Value(name, type)

class BoolConstant(val value: Boolean) : Constant(value.toString(), TF.getBoolType())
class ByteConstant(val value: Byte) : Constant(value.toString(), TF.getByteType())
class ShortConstant(val value: Short) : Constant(value.toString(), TF.getShortType())
class IntConstant(val value: Int) : Constant(value.toString(), TF.getIntType())
class LongConstant(val value: Long) : Constant(value.toString(), TF.getLongType())
class CharConstant(val value: Char) : Constant(value.toString(), TF.getCharType())
class FloatConstant(val value: Float) : Constant(value.toString(), TF.getFloatType())
class DoubleConstant(val value: Double) : Constant(value.toString(), TF.getDoubleType())

class ClassConstant(val desc: String) : Constant(desc, parseDesc(desc))
class StringConstant(val value: String) : Constant("\"$value\"", TF.getRefType("java.lang.String"))
class MethodConstant(val method: Method) : Constant(method.name, TF.getRefType("java.lang.invoke.MethodHandle"))

class NullConstant : Constant("null", TF.getNullType()) {
    companion object {
        val instance = NullConstant()
    }
}