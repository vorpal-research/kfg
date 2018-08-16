package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.Type

sealed class Constant(name: String, type: Type) : Value(ConstantName(name), type)

data class BoolConstant(val value: Boolean) : Constant(value.toString(), TF.boolType) {
    override fun toString()= super.toString()
}

data class ByteConstant(val value: Byte) : Constant(value.toString(), TF.byteType) {
    override fun toString()= super.toString()
}

data class ShortConstant(val value: Short) : Constant(value.toString(), TF.shortType) {
    override fun toString()= super.toString()
}

data class IntConstant(val value: Int) : Constant(value.toString(), TF.intType) {
    override fun toString()= super.toString()
}

data class LongConstant(val value: Long) : Constant(value.toString(), TF.longType) {
    override fun toString()= super.toString()
}

data class CharConstant(val value: Char) : Constant(value.toString(), TF.charType) {
    override fun toString()= super.toString()
}

data class FloatConstant(val value: Float) : Constant(value.toString(), TF.floatType) {
    override fun toString()= super.toString()
}

data class DoubleConstant(val value: Double) : Constant(value.toString(), TF.doubleType) {
    override fun toString()= super.toString()
}

data class StringConstant(val value: String) : Constant("\"$value\"", TF.stringType) {
    override fun toString()= super.toString()
}

data class MethodConstant(val method: Method) : Constant(method.name, TF.getRefType("java/lang/invoke/MethodHandle")) {
    override fun toString()= super.toString()
}

class ClassConstant(`class`: Type) : Constant("${`class`.name}.class", `class`)
object NullConstant : Constant("null", TF.nullType)