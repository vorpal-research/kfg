package org.jetbrains.research.kfg.value

import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.value.expr.*

class ValueFactory {
    val exprFactory = ExprFactory()

    private object Holder {
        val instance = ValueFactory()
    }

    companion object {
        val instance: ValueFactory by lazy { instance }
    }

    fun getLocal(indx: Int, type: Type): Value = Local(indx, type)
    fun getField(name: String, klass: Class, type: Type): Value = Field(name, klass, type)
    fun getField(name: String, klass: Class, type: Type, obj: Value) = Field(name, klass, type, obj)
    // constants
    fun getNullConstant(): Value = NullConstant.instance
    fun getIntConstant(value: Int): Value = IntConstant(value)
    fun getLongConstant(value: Long): Value = LongConstant(value)
    fun getFloatConstant(value: Float): Value = FloatConstant(value)
    fun getDoubleConstant(value: Double): Value = DoubleConstant(value)
    fun getStringConstant(value: String): Value = StringConstant(value)
    fun getClassConstant(desc: String): Value = ClassConstant(desc)
    fun getMethodConstant(method: Method): Value = MethodConstant(method)
}