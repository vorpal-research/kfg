package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ClassManager

object TypeFactory {
    fun getVoidType(): Type = VoidType
    fun getBoolType(): Type = BoolType
    fun getByteType(): Type = ByteType
    fun getShortType(): Type = ShortType
    fun getIntType(): Type = IntType
    fun getLongType(): Type = LongType
    fun getCharType(): Type = CharType
    fun getFloatType(): Type = FloatType
    fun getDoubleType(): Type = DoubleType
    fun getRefType(cname: Class): Type = ClassType(cname)
    fun getRefType(cname: String): Type = getRefType(CM.getByName(cname))
    fun getArrayType(component: Type): Type = ArrayType(component)
    fun getNullType(): Type = NullType
}