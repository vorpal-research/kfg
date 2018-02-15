package org.jetbrains.research.kfg.value

import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.Type

class ValueFactory {
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

    // terms
    fun getArrayLoad(arrayRef: Value, index: Value): Value {
        val type = arrayRef.type as ArrayType
        return ArrayLoadValue(type, arrayRef, index)
    }

    fun getNewArray(type: Type, count: Value): Value = NewArrayValue(type, count)

    fun getNew(type: Type): Value = NewValue(type)

    fun getCheckCast(type: Type, obj: Value): Value = CheckCastValue(type, obj)

    fun getInstanceOf(obj: Value): Value = InstanceOfValue(obj)

    fun getBinary(opcode: BinaryOpcode, lhv: Value, rhv: Value): Value = BinaryValue(opcode, lhv, rhv)

    fun getCast(type: Type, obj: Value): Value = CastValue(type, obj)

    fun getCmp(opcode: CmpOpcode, lhv: Value, rhv: Value): Value = CmpValue(opcode, lhv, rhv)

    fun getArrayLength(arrayRef: Value): Value = ArrayLengthValue(arrayRef)
}