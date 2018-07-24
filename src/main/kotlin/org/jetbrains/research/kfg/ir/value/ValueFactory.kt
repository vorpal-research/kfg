package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.InvalidStateError
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.*

object ValueFactory {
    fun getThis(type: Type) = ThisRef(type)
    fun getArgument(index: Int, method: Method, type: Type) = Argument(index, method, type)
    // constants
    fun getNullConstant() = NullConstant
    fun getBoolConstant(value: Boolean) = BoolConstant(value)
    fun getByteConstant(value: Byte) = ByteConstant(value)
    fun getCharConstant(value: Char) = CharConstant(value)
    fun getIntConstant(value: Int) = IntConstant(value)
    fun getLongConstant(value: Long) = LongConstant(value)
    fun getFloatConstant(value: Float) = FloatConstant(value)
    fun getDoubleConstant(value: Double) = DoubleConstant(value)
    fun getStringConstant(value: String) = StringConstant(value)
    fun getClassConstant(desc: String) = ClassConstant(parseDesc(desc))
    fun getMethodConstant(method: Method) = MethodConstant(method)

    fun getZeroConstant(type: Type) = when(type) {
        is BoolType -> getBoolConstant(false)
        is ByteType -> getByteConstant(0.toByte())
        is CharType -> getCharConstant(0.toChar())
        is IntType -> getIntConstant(0)
        is LongType -> getLongConstant(0)
        is FloatType -> getFloatConstant(0.0f)
        is DoubleType -> getDoubleConstant(0.0)
        is Reference -> getNullConstant()
        else -> throw InvalidStateError("Unknown type: ${type.name}")
    }

    fun getConstant(value: Any?) = when (value) {
        is Int -> getIntConstant(value)
        is Float -> getFloatConstant(value)
        is Long -> getLongConstant(value)
        is Double -> getDoubleConstant(value)
        is String -> getStringConstant(value)
        else -> null
    }

    fun unwrapConstant(constant: Value?): Any? = when (constant) {
        is BoolConstant -> if (constant.value) 1 else 0
        is ByteConstant -> constant.value.toInt()
        is ShortConstant -> constant.value.toInt()
        is IntConstant -> constant.value
        is LongConstant -> constant.value
        is CharConstant -> constant.value.toInt()
        is FloatConstant -> constant.value
        is DoubleConstant -> constant.value
        is StringConstant -> constant.value
        else -> null
    }
}