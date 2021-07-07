package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.UnknownInstanceException
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kthelper.defaultHashCode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

abstract class Class(
    cm: ClassManager,
    val cn: ClassNode
) : Node(cm, cn.name.substringAfterLast(Package.SEPARATOR), cn.access) {
    protected data class MethodKey(val name: String, val desc: MethodDesc) {
        constructor(tf: TypeFactory, name: String, desc: String) : this(name, MethodDesc.fromDesc(tf, desc))

        override fun toString() = "$name$desc"
    }

    protected infix fun String.to(desc: MethodDesc) = MethodKey(this, desc)

    protected data class FieldKey(val name: String, val type: Type)

    protected infix fun String.to(type: Type) = FieldKey(this, type)

    protected val innerMethods = mutableMapOf<MethodKey, Method>()
    protected val innerFields = mutableMapOf<FieldKey, Field>()
    val pkg = Package.parse(
        cn.name.substringBeforeLast(Package.SEPARATOR, "")
    )

    val allMethods get() = innerMethods.values.toSet()
    val constructors get() = allMethods.filter { it.isConstructor }.toSet()
    val methods get() = allMethods.filterNot { it.isConstructor }.toSet()
    val fields get() = innerFields.values.toSet()

    internal val failingMethods = mutableSetOf<Method>()

    val fullName
        get() = if (pkg == Package.emptyPackage) name else "$pkg${Package.SEPARATOR}$name"

    val canonicalDesc
        get() = fullName.replace(Package.SEPARATOR, Package.CANONICAL_SEPARATOR)

    val superClass
        get() = cn.superName?.let { cm[it] }

    val interfaces
        get() = cn.interfaces.map { cm[it] }

    val outerClass
        get() = cn.outerClass?.let { cm[it] }

    val outerMethod
        get() = cn.outerMethod?.let { outerClass?.getMethod(it, cn.outerMethodDesc!!) }

    val innerClasses
        get() = cn.innerClasses?.map { cm[it.name] } ?: listOf()

    override val asmDesc
        get() = "L$fullName;"

    fun init() {
        for (fieldNode in cn.fields) {
            val field = Field(cm, fieldNode, this)
            innerFields[field.name to field.type] = field
        }
        cn.methods.forEach {
            val desc = MethodDesc.fromDesc(cm.type, it.desc)
            innerMethods[it.name to desc] = Method(cm, it, this)
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

    fun modifyField(field: Field, type: Type): Field {
        innerFields.remove(field.name to field.type)
        field.type = type
        innerFields[field.name to field.type] = field
        return field
    }

    fun modifyMethod(method: Method, desc: MethodDesc): Method {
        innerMethods.remove(method.name to method.desc)
        method.desc = desc
        innerMethods[method.name to method.desc] = method
        return method
    }

    override fun toString() = fullName
    override fun hashCode() = defaultHashCode(name, pkg)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Class
        return this.name == other.name && this.pkg == other.pkg
    }
}

class ConcreteClass(cm: ClassManager, cn: ClassNode) : Class(cm, cn) {
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

class OuterClass(cm: ClassManager, cn: ClassNode) : Class(cm, cn) {
    override fun getFieldConcrete(name: String, type: Type) = getField(name, type)
    override fun getMethodConcrete(name: String, desc: MethodDesc) = getMethod(name, desc)

    override fun getField(name: String, type: Type): Field = innerFields.getOrPut(name to type) {
        val fn = FieldNode(0, name, type.asmDesc, null, null)
        Field(cm, fn, this)
    }

    override fun getMethod(name: String, desc: MethodDesc): Method {
        val methodDesc = name to desc
        return innerMethods.getOrPut(methodDesc) {
            val mn = MethodNode()
            mn.name = name
            mn.desc = desc.asmDesc
            Method(cm, mn, this)
        }
    }

    override fun isAncestorOf(other: Class) = true
}