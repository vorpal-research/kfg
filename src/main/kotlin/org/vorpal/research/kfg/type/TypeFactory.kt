package org.vorpal.research.kfg.type

import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kthelper.assert.unreachable
import java.lang.Class as JClass

class TypeFactory internal constructor(val cm: ClassManager) {
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

    val primaryWrapperTypes: Set<Type>
        get() = primaryTypes.map { getWrapper(it) }.toSet()

    val nullType: Type
        get() = NullType

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
        else -> unreachable("Unknown primary type $type")
    }

    fun getUnwrapped(type: Type): PrimaryType? = when (type) {
        boolWrapper -> boolType
        byteWrapper -> byteType
        charWrapper -> charType
        shortWrapper -> shortType
        intWrapper -> intType
        longWrapper -> longType
        floatWrapper -> floatType
        doubleWrapper -> doubleType
        else -> null
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
            else -> unreachable("Unknown primary type $klass")
        }

        klass.isArray -> getArrayType(get(klass.componentType))
        else -> getRefType(cm[klass.name.replace(Package.CANONICAL_SEPARATOR, Package.SEPARATOR)])
    }

}

val TypeFactory.classType
    get() = getRefType(SystemTypeNames.classClass)

val TypeFactory.stringType
    get() = getRefType(SystemTypeNames.stringClass)

val TypeFactory.objectType
    get() = getRefType(SystemTypeNames.objectClass)

val TypeFactory.boolWrapper: Type
    get() = getRefType(SystemTypeNames.booleanClass)

val TypeFactory.byteWrapper: Type
    get() = getRefType(SystemTypeNames.byteClass)

val TypeFactory.charWrapper: Type
    get() = getRefType(SystemTypeNames.charClass)

val TypeFactory.shortWrapper: Type
    get() = getRefType(SystemTypeNames.shortClass)

val TypeFactory.intWrapper: Type
    get() = getRefType(SystemTypeNames.integerClass)

val TypeFactory.longWrapper: Type
    get() = getRefType(SystemTypeNames.longClass)

val TypeFactory.floatWrapper: Type
    get() = getRefType(SystemTypeNames.floatClass)

val TypeFactory.doubleWrapper: Type
    get() = getRefType(SystemTypeNames.doubleClass)

val TypeFactory.collectionType: Type
    get() = getRefType(SystemTypeNames.collectionClass)

val TypeFactory.listType: Type
    get() = getRefType(SystemTypeNames.listClass)

val TypeFactory.arrayListType: Type
    get() = getRefType(SystemTypeNames.arrayListClass)

val TypeFactory.linkedListType: Type
    get() = getRefType(SystemTypeNames.linkedListClass)

val TypeFactory.setType: Type
    get() = getRefType(SystemTypeNames.setClass)

val TypeFactory.hashSetType: Type
    get() = getRefType(SystemTypeNames.hashSetClass)

val TypeFactory.treeSetType: Type
    get() = getRefType(SystemTypeNames.treeSetClass)

val TypeFactory.mapType: Type
    get() = getRefType(SystemTypeNames.setClass)

val TypeFactory.hashMapType: Type
    get() = getRefType(SystemTypeNames.hashMapClass)

val TypeFactory.treeMapType: Type
    get() = getRefType(SystemTypeNames.treeMapClass)

val TypeFactory.objectArrayClass
    get() = getRefType(cm["$objectType[]"])

val TypeFactory.classLoaderType
    get() = getRefType(SystemTypeNames.classLoader)