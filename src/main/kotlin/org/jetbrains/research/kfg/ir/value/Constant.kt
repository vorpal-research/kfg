package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.Type

sealed class Constant(name: String, type: Type) : Value(ConstantName(name), type)

data class BoolConstant(val value: Boolean) : Constant(value.toString(), TF.getBoolType())

data class ByteConstant(val value: Byte) : Constant(value.toString(), TF.getByteType())

data class ShortConstant(val value: Short) : Constant(value.toString(), TF.getShortType())

data class IntConstant(val value: Int) : Constant(value.toString(), TF.getIntType())

data class LongConstant(val value: Long) : Constant(value.toString(), TF.getLongType())

data class CharConstant(val value: Char) : Constant(value.toString(), TF.getCharType())

data class FloatConstant(val value: Float) : Constant(value.toString(), TF.getFloatType())

data class DoubleConstant(val value: Double) : Constant(value.toString(), TF.getDoubleType())

data class StringConstant(val value: String) : Constant("\"$value\"", TF.getString())

data class MethodConstant(val method: Method) : Constant(method.name, TF.getRefType("java/lang/invoke/MethodHandle"))

class ClassConstant(`class`: Type) : Constant("${`class`.name}.class", `class`)
object NullConstant : Constant("null", TF.getNullType())