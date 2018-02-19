package org.jetbrains.research.kfg.value

import org.jetbrains.research.kfg.UnexpectedException
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.*
import org.jetbrains.research.kfg.value.expr.*

class ValueFactory private constructor() {
    val exprFactory = ExprFactory()

    companion object {
        val instance = ValueFactory()
    }

    fun getLocal(indx: Int, type: Type): Value = Local(indx, type)
    fun getField(name: String, klass: Class, type: Type): Value = Field(name, klass, type)
    fun getField(name: String, klass: Class, type: Type, obj: Value) = Field(name, klass, type, obj)
    // constants
    fun getNullConstant(): Value = NullConstant.instance
    fun getBoolConstant(value: Boolean) = BoolConstant(value)
    fun getIntConstant(value: Int): Value = IntConstant(value)
    fun getLongConstant(value: Long): Value = LongConstant(value)
    fun getFloatConstant(value: Float): Value = FloatConstant(value)
    fun getDoubleConstant(value: Double): Value = DoubleConstant(value)
    fun getStringConstant(value: String): Value = StringConstant(value)
    fun getClassConstant(desc: String): Value = ClassConstant(desc)
    fun getMethodConstant(method: Method): Value = MethodConstant(method)

    fun getZeroConstant(type: Type): Value = when(type) {
        is BoolType -> getBoolConstant(false)
        is IntType -> getIntConstant(0)
        is LongType -> getLongConstant(0)
        is FloatType -> getFloatConstant(0.0f)
        is DoubleType -> getDoubleConstant(0.0)
        is Reference -> getNullConstant()
        else -> throw UnexpectedException("Unknown type: ${type.getName()}")
    }
}