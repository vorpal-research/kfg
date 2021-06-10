package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import java.lang.Class as JClass

class TypeFactory(val cm: ClassManager) {
    private val klassTypeHash = mutableMapOf<Class, Type>()
    private val arrayTypeHash = mutableMapOf<Type, Type>()

    val voidType: Type
        get() = VoidType

    val boolType: PrimaryType
        get() = BoolType

    val byteType: PrimaryType
        get() = ByteType

    val shortType: PrimaryType
        get() = ShortType

    val intType: PrimaryType
        get() = IntType

    val longType: PrimaryType
        get() = LongType

    val charType: PrimaryType
        get() = CharType

    val floatType: PrimaryType
        get() = FloatType

    val doubleType: PrimaryType
        get() = DoubleType

    val primaryTypes: Set<PrimaryType> by lazy {
        setOf(
            boolType,
            byteType,
            shortType,
            intType,
            longType,
            charType,
            floatType,
            doubleType
        )
    }

    val nullType: Type
        get() = NullType

    val stringType
        get() = getRefType(SystemTypeNames.stringClass)

    val objectType
        get() = getRefType(SystemTypeNames.objectClass)

    val boolWrapper: Type
        get() = getRefType(SystemTypeNames.booleanClass)

    val byteWrapper: Type
        get() = getRefType(SystemTypeNames.byteClass)

    val charWrapper: Type
        get() = getRefType(SystemTypeNames.charClass)

    val shortWrapper: Type
        get() = getRefType(SystemTypeNames.shortClass)

    val intWrapper: Type
        get() = getRefType(SystemTypeNames.integerClass)

    val longWrapper: Type
        get() = getRefType(SystemTypeNames.longClass)

    val floatWrapper: Type
        get() = getRefType(SystemTypeNames.floatClass)

    val doubleWrapper: Type
        get() = getRefType(SystemTypeNames.doubleClass)

    val collectionType: Type
        get() = getRefType(SystemTypeNames.collectionClass)

    val listType: Type
        get() = getRefType(SystemTypeNames.listClass)

    val arrayListType: Type
        get() = getRefType(SystemTypeNames.arrayListClass)

    val linkedListType: Type
        get() = getRefType(SystemTypeNames.linkedListClass)

    val setType: Type
        get() = getRefType(SystemTypeNames.setClass)

    val hashSetType: Type
        get() = getRefType(SystemTypeNames.hashSetClass)

    val treeSetType: Type
        get() = getRefType(SystemTypeNames.treeSetClass)

    val mapType: Type
        get() = getRefType(SystemTypeNames.setClass)

    val hashMapType: Type
        get() = getRefType(SystemTypeNames.hashMapClass)

    val treeMapType: Type
        get() = getRefType(SystemTypeNames.treeMapClass)

    val objectArrayClass
        get() = getRefType(cm["$objectType[]"])

    fun getRefType(cname: Class): Type = klassTypeHash.getOrPut(cname) { ClassType(cname) }
    fun getRefType(cname: String): Type = getRefType(cm[cname])
    fun getArrayType(component: Type): Type = arrayTypeHash.getOrPut(component) { ArrayType(component) }

    fun getWrapper(type: PrimaryType): Type = when (type) {
        is BoolType -> boolWrapper
        is ByteType -> byteWrapper
        is CharType -> charWrapper
        is ShortType -> shortWrapper
        is IntType -> intWrapper
        is LongType -> longWrapper
        is FloatType -> floatWrapper
        is DoubleType -> doubleWrapper
        else -> unreachable { log.error("Unknown primary type $type") }
    }

    fun get(klass: JClass<*>): Type = when {
        klass.isPrimitive -> when (klass) {
            Void::class.java -> voidType
            Boolean::class.javaPrimitiveType -> boolType
            Byte::class.javaPrimitiveType -> byteType
            Char::class.javaPrimitiveType -> charType
            Short::class.javaPrimitiveType -> shortType
            Int::class.javaPrimitiveType -> intType
            Long::class.javaPrimitiveType -> longType
            Float::class.javaPrimitiveType -> floatType
            Double::class.javaPrimitiveType -> doubleType
            else -> unreachable { log.error("Unknown primary type $klass") }
        }
        klass.isArray -> getArrayType(get(klass.componentType))
        else -> getRefType(cm[klass.name.replace(Package.CANONICAL_SEPARATOR, Package.SEPARATOR)])
    }

}