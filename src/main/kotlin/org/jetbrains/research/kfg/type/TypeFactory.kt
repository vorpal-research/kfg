package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ClassManager

class TypeFactory(val cm: ClassManager) {
    companion object {
        const val stringClass = "java/lang/String"
        const val objectClass = "java/lang/Object"
    }

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
        get() = getRefType(stringClass)

    val objectType
        get() = getRefType(objectClass)

    val objectArrayClass
        get() = getRefType(cm.getByName("$objectType[]"))

    fun getRefType(cname: Class): Type = ClassType(cname)
    fun getRefType(cname: String): Type = getRefType(cm.getByName(cname))
    fun getArrayType(component: Type): Type = ArrayType(component)
}