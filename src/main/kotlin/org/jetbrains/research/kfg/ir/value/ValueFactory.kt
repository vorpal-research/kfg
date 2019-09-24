package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.InvalidStateError
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.*

class ValueFactory(val cm: ClassManager) {
    val types get() = cm.type

    fun getThis(type: Type): Value = ThisRef(type)
    fun getThis(klass: Class): Value = getThis(types.getRefType(klass))
    fun getArgument(index: Int, method: Method, type: Type): Value = Argument(index, method, type)
    // constants
    fun getNullConstant(): Value = NullConstant(types.nullType)
    fun getBoolConstant(value: Boolean): Value = BoolConstant(value, types.boolType)
    fun getByteConstant(value: Byte): Value = ByteConstant(value, types.byteType)
    fun getCharConstant(value: Char): Value = CharConstant(value, types.charType)
    fun getShortConstant(value: Short): Value = ShortConstant(value, types.shortType)
    fun getIntConstant(value: Int): Value = IntConstant(value, types.intType)
    fun getLongConstant(value: Long): Value = LongConstant(value, types.longType)
    fun getFloatConstant(value: Float): Value = FloatConstant(value, types.floatType)
    fun getDoubleConstant(value: Double): Value = DoubleConstant(value, types.doubleType)
    fun getStringConstant(value: String): Value = StringConstant(value, types.stringType)
    fun getClassConstant(desc: String): Value = ClassConstant(parseDesc(types, desc))
    fun getClassConstant(klass: Class): Value = ClassConstant(types.getRefType(klass))
    fun getMethodConstant(method: Method): Value = MethodConstant(method, types.getRefType("java/lang/invoke/MethodHandle"))

    fun getZeroConstant(type: Type): Value = when(type) {
        is BoolType -> getBoolConstant(false)
        is ByteType -> getByteConstant(0.toByte())
        is CharType -> getCharConstant(0.toChar())
        is ShortType -> getShortConstant(0)
        is IntType -> getIntConstant(0)
        is LongType -> getLongConstant(0)
        is FloatType -> getFloatConstant(0.0f)
        is DoubleType -> getDoubleConstant(0.0)
        is Reference -> getNullConstant()
        else -> throw InvalidStateError("Unknown type: ${type.name}")
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