package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.UnexpectedException
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.*

object ValueFactory {
    fun getThis(type: Type): Value = ThisRef(type)
    fun getArgument(name: String, method: Method, type: Type): Value = Argument(name, method, type)
    // constants
    fun getNullConstant(): Value = NullConstant
    fun getBoolConstant(value: Boolean): Value = BoolConstant(value)
    fun getCharConstant(value: Char): Value = CharConstant(value)
    fun getIntConstant(value: Int): Value = IntConstant(value)
    fun getLongConstant(value: Long): Value = LongConstant(value)
    fun getFloatConstant(value: Float): Value = FloatConstant(value)
    fun getDoubleConstant(value: Double): Value = DoubleConstant(value)
    fun getStringConstant(value: String): Value = StringConstant(value)
    fun getClassConstant(desc: String): Value = ClassConstant(parseDesc(desc))
    fun getMethodConstant(method: Method): Value = MethodConstant(method)

    fun getZeroConstant(type: Type): Value = when(type) {
        is BoolType -> getBoolConstant(false)
        is CharType -> getCharConstant(0.toChar())
        is IntType -> getIntConstant(0)
        is LongType -> getLongConstant(0)
        is FloatType -> getFloatConstant(0.0f)
        is DoubleType -> getDoubleConstant(0.0)
        is Reference -> getNullConstant()
        else -> throw UnexpectedException("Unknown type: ${type.name}")
    }

    fun getConstant(value: Any?): Value? = when (value) {
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