package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.UnknownInstance
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.util.simpleHash
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

abstract class Class(cm: ClassManager, val cn: ClassNode) : Node(cm, cn.name.substringAfterLast('/'), cn.access) {
    data class MethodKey(val name: String, val desc: MethodDesc) {
        constructor(tf: TypeFactory, name: String, desc: String) : this(name, MethodDesc.fromDesc(tf, desc))
        override fun toString() = "$name$desc"
    }

    data class FieldKey(val name: String, val type: Type)

    protected val innerMethods = mutableMapOf<MethodKey, Method>()
    protected val innerFields = mutableMapOf<FieldKey, Field>()
    val `package` = Package(cn.name.substringBeforeLast('/', ""))

    val methods get() = innerMethods.values.toSet()
    val fields get() = innerFields.values.toSet()

    val fullname
        get() = if (`package` == Package.emptyPackage) name else "$`package`/$name"

    val canonicalDesc
        get() = fullname.replace('/', '.')

    val superClass
        get() = if (cn.superName != null) cm.getByName(cn.superName) else null

    val interfaces
        get() = if (cn.interfaces != null) cn.interfaces.map { cm.getByName(it as String) } else listOf()

    val outerClass
        get() = if (cn.outerClass != null) cm.getByName(cn.outerClass) else null

    val outerMethod
        get() = if (cn.outerMethod != null) outerClass?.getMethod(cn.outerMethod, cn.outerMethodDesc) else null

    val innerClasses
        get() = if (cn.innerClasses != null) cn.innerClasses.map { cm.getByName(it as String) } else listOf()

    override val asmDesc
        get() = "L$fullname;"

    fun init() {
        addVisibleAnnotations(@Suppress("UNCHECKED_CAST") (cn.visibleAnnotations as List<AnnotationNode>?))
        addInvisibleAnnotations(@Suppress("UNCHECKED_CAST") (cn.invisibleAnnotations as List<AnnotationNode>?))
        cn.fields.forEach {
            it as FieldNode
            val field = Field(cm, it, this)
            innerFields[FieldKey(field.name, field.type)] = field
        }
        cn.methods.forEach {
            it as MethodNode
            innerMethods[MethodKey(cm.type, it.name, it.desc)] = Method(cm, it, this)
        }
    }

    val allAncestors get() = listOfNotNull(superClass) + interfaces

    abstract fun isAncestor(other: Class): Boolean

    abstract fun getFieldConcrete(name: String, type: Type): Field?
    abstract fun getMethodConcrete(name: String, desc: MethodDesc): Method?

    abstract fun getField(name: String, type: Type): Field

    fun getMethod(name: String, desc: String) = getMethod(name, MethodDesc.fromDesc(cm.type, desc))
    abstract fun getMethod(name: String, desc: MethodDesc): Method

    override fun toString() = fullname
    override fun hashCode() = simpleHash(name, `package`)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Class
        return this.name == other.name && this.`package` == other.`package`
    }
}

class ConcreteClass(cm: ClassManager, cn: ClassNode) : Class(cm, cn) {
    override fun getFieldConcrete(name: String, type: Type): Field? =
            innerFields.getOrElse(FieldKey(name, type)) { superClass?.getFieldConcrete(name, type) }

    override fun getMethodConcrete(name: String, desc: MethodDesc): Method? = innerMethods.getOrElse(MethodKey(name, desc)) {
        val uppers = listOf(superClass).asSequence().plus(interfaces).filterNotNull().toList()
        val res: Method? = uppers
                .asSequence()
                .mapNotNull { it as? ConcreteClass }
                .map { it.getMethodConcrete(name, desc) }
                .firstOrNull() ?: uppers
                .asSequence()
                .mapNotNull { it as? OuterClass }
                .firstOrNull()
                ?.getMethodConcrete(name, desc)
        res
    }

    override fun getField(name: String, type: Type) = innerFields.getOrElse(FieldKey(name, type)) {
        val parents = (listOf(superClass) + interfaces).filterNotNull()
        parents.mapNotNull { it as? ConcreteClass }.mapNotNull { it.getFieldConcrete(name, type) }.firstOrNull()
                ?: parents.mapNotNull { it as? OuterClass }.map { it.getFieldConcrete(name, type) }.firstOrNull()
                ?: throw UnknownInstance("No field \"$name\" in class $this")
    }

    override fun getMethod(name: String, desc: MethodDesc): Method {
        val methodDesc = MethodKey(name, desc)
        return innerMethods.getOrElse(methodDesc) {
            val parents = (listOf(superClass) + interfaces).filterNotNull()
            parents.mapNotNull { it as? ConcreteClass }.mapNotNull { it.getMethodConcrete(name, desc) }.firstOrNull()
                    ?: parents.mapNotNull { it as? OuterClass }.map { it.getMethodConcrete(name, desc) }.firstOrNull()
                    ?: throw UnknownInstance("No method \"$methodDesc\" in $this")
        }
    }

    override fun isAncestor(other: Class): Boolean {
        if (this == other) return true
        else {
            val ancestors = other.allAncestors
            for (it in ancestors) if (isAncestor(it)) return true
        }
        return false
    }
}

class OuterClass(cm: ClassManager, cn: ClassNode) : Class(cm, cn) {
    override fun getFieldConcrete(name: String, type: Type) = getField(name, type)
    override fun getMethodConcrete(name: String, desc: MethodDesc) = getMethod(name, desc)

    override fun getField(name: String, type: Type): Field = innerFields.getOrPut(FieldKey(name, type)) {
        val fn = FieldNode(0, name, type.asmDesc, null, null)
        Field(cm, fn, this)
    }

    override fun getMethod(name: String, desc: MethodDesc): Method {
        val methodDesc = MethodKey(name, desc)
        return innerMethods.getOrPut(methodDesc) {
            val mn = MethodNode()
            mn.name = name
            mn.desc = desc.asmDesc
            Method(cm, mn, this)
        }
    }

    override fun isAncestor(other: Class) = true
}