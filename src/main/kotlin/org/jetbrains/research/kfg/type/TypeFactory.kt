package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ClassManager

class TypeFactory private constructor() {

    private object Holder {
        val instance = TypeFactory()
    }

    companion object {
        val instance: TypeFactory by lazy { Holder.instance }
    }

    fun getVoidType(): Type = VoidType.instance
    fun getBoolType(): Type = BoolType.instance
    fun getByteType(): Type = ByteType.instance
    fun getShortType(): Type = ShortType.instance
    fun getIntType(): Type = IntType.instance
    fun getLongType(): Type = LongType.instance
    fun getCharType(): Type = CharType.instance
    fun getFloatType(): Type = FloatType.instance
    fun getDoubleType(): Type = DoubleType.instance
    fun getRefType(cname: Class): Type = ClassType(cname)
    fun getRefType(cname: String): Type = getRefType(CM.getByName(cname))
    fun getArrayType(component: Type): Type = ArrayType(component)
    fun getNullType(): Type = NullType.instance
}