package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.InvalidStateException
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.UnknownInstanceException
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.defaultHashCode
import org.objectweb.asm.tree.ClassNode

abstract class Class : Node {
    protected data class MethodKey(val name: String, val desc: MethodDesc) {
        constructor(tf: TypeFactory, name: String, desc: String) : this(name, MethodDesc.fromDesc(tf, desc))

        override fun toString() = "$name$desc"
    }

    protected infix fun String.to(desc: MethodDesc) = MethodKey(this, desc)

    protected data class FieldKey(val name: String, val type: Type)

    protected infix fun String.to(type: Type) = FieldKey(this, type)

    internal val cn: ClassNode
    val pkg: Package
    protected val innerMethods = mutableMapOf<MethodKey, Method>()
    protected val innerFields = mutableMapOf<FieldKey, Field>()
    protected var superClassName: String? = null
    protected val interfaceNames = mutableSetOf<String>()
    protected var outerClassName: String? = null
    protected var outerMethodName: String? = null
    protected var outerMethodDesc: String? = null
    protected var innerClassesMap = mutableMapOf<String, Modifiers>()

    val allMethods get() = innerMethods.values.toSet()
    val constructors get() = allMethods.filter { it.isConstructor }.toSet()
    val methods get() = allMethods.filterNot { it.isConstructor }.toSet()
    val fields get() = innerFields.values.toSet()

    internal val failingMethods = mutableSetOf<Method>()

    val fullName
        get() = if (pkg == Package.emptyPackage) name else "$pkg${Package.SEPARATOR}$name"

    val canonicalDesc
        get() = fullName.replace(Package.SEPARATOR, Package.CANONICAL_SEPARATOR)

    var superClass
        get() = superClassName?.let { cm[it] }
        set(value) {
            superClassName = value?.fullName
        }

    val interfaces get() = interfaceNames.map { cm[it] }.toSet()

    var outerClass
        get() = outerClassName?.let { cm[it] }
        set(value) {
            outerClassName = value?.fullName
        }

    var outerMethod
        get() = outerMethodName?.let { name ->
            outerMethodDesc?.let { desc ->
                outerClass?.getMethod(name, desc)
            }
        }
        set(value) {
            outerMethodName = value?.name
            outerMethodDesc = value?.desc?.asmDesc
            outerClass = value?.klass
        }

    val innerClasses get() = innerClassesMap.map { cm[it.key] to it.value }.toMap()

    override val asmDesc
        get() = "L$fullName;"

    constructor(
        cm: ClassManager,
        pkg: Package,
        name: String,
        modifiers: Modifiers = Modifiers(0)
    ) : super(cm, name, modifiers) {
        ktassert(pkg.isConcrete)
        this.pkg = pkg
        this.cn = ClassNode()
        this.cn.name = fullName
        this.cn.access = modifiers.value
    }

    constructor(
        cm: ClassManager,
        cn: ClassNode
    ) : super(cm, cn.name.substringAfterLast(Package.SEPARATOR), Modifiers(cn.access)) {
        this.cn = cn
        this.pkg = Package.parse(
            cn.name.substringBeforeLast(Package.SEPARATOR, "")
        )
        this.superClassName = cn.superName
        this.interfaceNames.addAll(cn.interfaces.toMutableSet())
        this.outerClassName = cn.outerClass
        this.outerMethodName = cn.outerMethod
        this.outerMethodDesc = cn.outerMethodDesc
        this.innerClassesMap.putAll(cn.innerClasses.map { it.name to Modifiers(it.access) }.toMutableSet())
    }

    internal fun init() {
        for (fieldNode in cn.fields) {
            val field = Field(cm, this, fieldNode)
            innerFields[field.name to field.type] = field
        }
        cn.methods.forEach {
            val desc = MethodDesc.fromDesc(cm.type, it.desc)
            innerMethods[it.name to desc] = Method(cm, this, it)
        }
        cn.methods = this.allMethods.map { it.mn }
    }

    val allAncestors get() = listOfNotNull(superClass) + interfaces

    fun toType() = cm.type.getRefType(this)

    abstract fun isAncestorOf(other: Class): Boolean
    fun isInheritorOf(other: Class) = other.isAncestorOf(this)

    abstract fun getFieldConcrete(name: String, type: Type): Field?
    abstract fun getMethodConcrete(name: String, desc: MethodDesc): Method?

    fun getFields(name: String) = fields.filter { it.name == name }.toSet()
    abstract fun getField(name: String, type: Type): Field

    fun getMethods(name: String) = methods.filter { it.name == name }.toSet()
    fun getMethod(name: String, desc: String) = getMethod(name, MethodDesc.fromDesc(cm.type, desc))
    fun getMethod(name: String, returnType: Type, vararg argTypes: Type) =
        this.getMethod(name, MethodDesc(argTypes, returnType))

    abstract fun getMethod(name: String, desc: MethodDesc): Method

    /**
     * creates a new field with given name and type and adds is to this klass
     * @throws InvalidStateException if there already exists field with given parameters
     */
    fun addField(name: String, type: Type): Field {
        if ((name to type) in innerFields) throw InvalidStateException("Field $name: $type already exists in $this")
        val field = Field(cm, this, name, type)
        innerFields[name to type] = field
        return field
    }

    /**
     * creates a new method with given name and descriptor and adds is to this klass
     * @throws InvalidStateException if there already exists method with given parameters
     */
    fun addMethod(name: String, desc: MethodDesc): Method {
        if ((name to desc) in innerMethods) throw InvalidStateException("Method $name: $desc already exists in $this")
        val method = Method(cm, this, name, desc)
        innerMethods[name to desc] = method
        return method
    }

    fun addMethod(name: String, returnType: Type, vararg argTypes: Type) =
        addMethod(name, MethodDesc(argTypes, returnType))

    fun removeField(field: Field) = innerFields.remove(field.name to field.type)
    fun removeMethod(method: Method) = innerMethods.remove(method.name to method.desc)

    override fun toString() = fullName
    override fun hashCode() = defaultHashCode(name, pkg)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Class
        return this.name == other.name && this.pkg == other.pkg
    }
}

class ConcreteClass : Class {
    constructor(cm: ClassManager, cn: ClassNode) : super(cm, cn)
    constructor(
        cm: ClassManager,
        pkg: Package,
        name: String,
        modifiers: Modifiers = Modifiers(0)
    ) : super(cm, pkg, name, modifiers)

    override fun getFieldConcrete(name: String, type: Type): Field? =
        innerFields.getOrElse(name to type) { superClass?.getFieldConcrete(name, type) }

    override fun getMethodConcrete(name: String, desc: MethodDesc): Method? =
        innerMethods.getOrElse(name to desc) {
            val concreteMethod = allAncestors.mapNotNull { it as? ConcreteClass }
                .map { it.getMethodConcrete(name, desc) }
                .firstOrNull()
            val res: Method? = concreteMethod
                ?: allAncestors.mapNotNull { it as? OuterClass }
                    .firstOrNull()
                    ?.getMethodConcrete(name, desc)
            res
        }

    override fun getField(name: String, type: Type) = innerFields.getOrElse(name to type) {
        var parents = allAncestors.toList()

        var result: Field?
        do {
            result =
                parents.mapNotNull { it as? ConcreteClass }.mapNotNull { it.getFieldConcrete(name, type) }.firstOrNull()
            parents = parents.flatMap { it.allAncestors }
        } while (result == null && parents.isNotEmpty())

        result
            ?: allAncestors.mapNotNull { it as? OuterClass }.map { it.getFieldConcrete(name, type) }.firstOrNull()
            ?: throw UnknownInstanceException("No field \"$name\" in class $this")
    }

    override fun getMethod(name: String, desc: MethodDesc): Method {
        val methodDesc = name to desc
        return innerMethods.getOrElse(methodDesc) {
            var parents = allAncestors.toList()

            var result: Method?
            do {
                result = parents.mapNotNull { it as? ConcreteClass }.mapNotNull { it.getMethodConcrete(name, desc) }
                    .firstOrNull()
                parents = parents.flatMap { it.allAncestors }
            } while (result == null && parents.isNotEmpty())

            result
                ?: allAncestors.mapNotNull { it as? OuterClass }.map { it.getMethodConcrete(name, desc) }.firstOrNull()
                ?: throw UnknownInstanceException("No method \"$methodDesc\" in $this")
        }
    }

    override fun isAncestorOf(other: Class): Boolean {
        if (this == other) return true
        else {
            val ancestors = other.allAncestors
            for (it in ancestors) if (isAncestorOf(it)) return true
        }
        return false
    }
}

class OuterClass(
    cm: ClassManager,
    pkg: Package,
    name: String,
    modifiers: Modifiers = Modifiers(0)
) : Class(cm, pkg, name, modifiers) {
    override fun getFieldConcrete(name: String, type: Type) = getField(name, type)
    override fun getMethodConcrete(name: String, desc: MethodDesc) = getMethod(name, desc)

    override fun getField(name: String, type: Type): Field = innerFields.getOrPut(name to type) {
        addField(name, type)
    }

    override fun getMethod(name: String, desc: MethodDesc): Method {
        return innerMethods.getOrPut(name to desc) {
            addMethod(name, desc)
        }
    }

    override fun isAncestorOf(other: Class) = true
}