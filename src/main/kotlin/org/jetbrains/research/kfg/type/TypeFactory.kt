package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ClassManager

object TypeFactory {
    val voidType: Type
        get() = VoidType

    val boolType: Type
        get() = BoolType

    val byteType: Type
        get() = ByteType

    val shortType: Type
        get() = ShortType

    val intType: Type
        get() = IntType

    val longType: Type
        get() = LongType

    val charType: Type
        get() = CharType

    val floatType: Type
        get() = FloatType

    val doubleType: Type
        get() = DoubleType

    val nullType: Type
        get() = NullType

    val stringType
        get() = getRefType("java/lang/String")

    val objectType
        get() = getRefType("java/lang/Object")

    fun getRefType(cname: Class): Type = ClassType(cname)
    fun getRefType(cname: String): Type = getRefType(CM.getByName(cname))
    fun getArrayType(component: Type): Type = ArrayType(component)
}