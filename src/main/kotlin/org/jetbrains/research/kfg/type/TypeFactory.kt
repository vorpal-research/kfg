package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.UnknownTypeException
import org.jetbrains.research.kfg.ir.Class
import java.lang.Class as JClass

class TypeFactory(val cm: ClassManager) {
    companion object {
        const val stringClass = "java/lang/String"
        const val objectClass = "java/lang/Object"
        const val booleanClass = "java/lang/Boolean"
        const val byteClass = "java/lang/Byte"
        const val charClass = "java/lang/Character"
        const val shortClass = "java/lang/Short"
        const val integerClass = "java/lang/Integer"
        const val longClass = "java/lang/Long"
        const val floatClass = "java/lang/Float"
        const val doubleClass = "java/lang/Double"
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

    val boolWrapper: Type
        get() = getRefType(booleanClass)

    val byteWrapper: Type
        get() = getRefType(byteClass)

    val charWrapper: Type
        get() = getRefType(charClass)

    val shortWrapper: Type
        get() = getRefType(shortClass)

    val intWrapper: Type
        get() = getRefType(integerClass)

    val longWrapper: Type
        get() = getRefType(longClass)

    val floatWrapper: Type
        get() = getRefType(floatClass)

    val doubleWrapper: Type
        get() = getRefType(doubleClass)

    val objectArrayClass
        get() = getRefType(cm.getByName("$objectType[]"))

    fun getRefType(cname: Class): Type = ClassType(cname)
    fun getRefType(cname: String): Type = getRefType(cm.getByName(cname))
    fun getArrayType(component: Type): Type = ArrayType(component)

    fun getWrapper(type: Type): Type = when (type) {
        is BoolType -> boolWrapper
        is ByteType -> byteWrapper
        is CharType -> charWrapper
        is ShortType -> shortWrapper
        is IntType -> intWrapper
        is LongType -> longWrapper
        is FloatType -> floatWrapper
        is DoubleType -> doubleWrapper
        else -> throw IllegalArgumentException("Can't find wrapper for type $type")
    }

    fun get(klass: JClass<*>): Type = when {
        klass.isPrimitive -> when (klass) {
            Void::class.java -> voidType
            Boolean::class.javaPrimitiveType -> boolType
            Byte::class.javaPrimitiveType -> byteType
            Short::class.javaPrimitiveType -> shortType
            Int::class.javaPrimitiveType -> intType
            Long::class.javaPrimitiveType -> longType
            Float::class.javaPrimitiveType -> floatType
            Double::class.javaPrimitiveType -> doubleType
            else -> throw UnknownTypeException("Unknown primitive type $klass")
        }
        klass.isArray -> getArrayType(get(klass.componentType))
        else -> getRefType(cm.getByName(klass.canonicalName.replace('.', '/')))
    }
}