package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.UnknownInstance
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.util.simpleHash
import org.objectweb.asm.tree.*

abstract class Class(val cn: ClassNode) : Node(cn.name.substringAfterLast('/'), cn.access) {
    class MethodKey(val name: String, val desc: MethodDesc) {
        constructor(name: String, desc: String) : this(name, MethodDesc.fromDesc(desc))

        override fun hashCode() = simpleHash(name, desc)
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != this.javaClass) return false
            other as MethodKey
            return this.name == other.name && this.desc == other.desc
        }
    }

    data class FieldKey(val name: String, val type: Type)

    val `package` = Package(cn.name.substringBeforeLast('/'))
    val fields = mutableMapOf<FieldKey, Field>()
    val methods = mutableMapOf<MethodKey, Method>()

    val fullname
        get() = "$`package`/$name"

    val superClass
        get() = if (cn.superName != null) CM.getByName(cn.superName) else null

    val interfaces
        get() = if (cn.interfaces != null) cn.interfaces.map { CM.getByName(it as String) } else listOf()

    val outerClass
        get() = if (cn.outerClass != null) CM.getByName(cn.outerClass) else null

    val outerMethod
        get() = if (cn.outerMethod != null) outerClass?.getMethod(cn.outerMethod, cn.outerMethodDesc) else null

    val innerClasses
        get() = if (cn.innerClasses != null) cn.innerClasses.map { CM.getByName(it as String) } else listOf()

    override val asmDesc
        get() = "L${fullname};"

    fun init() {
        addVisibleAnnotations(@Suppress("UNCHECKED_CAST") (cn.visibleAnnotations as List<AnnotationNode>?))
        addInvisibleAnnotations(@Suppress("UNCHECKED_CAST") (cn.invisibleAnnotations as List<AnnotationNode>?))
        cn.fields.forEach {
            it as FieldNode
            val field = Field(it, this)
            fields.put(FieldKey(field.name, field.type), field)
        }
        cn.methods.forEach {
            it as MethodNode
            methods[MethodKey(it.name, it.desc)] = Method(it, this)
        }
    }

    fun getAllAncestors() = listOf(superClass).plus(interfaces).filterNotNull()

    abstract fun isAncestor(other: Class): Boolean

    abstract fun getFieldConcrete(name: String, type: Type): Field?
    abstract fun getMethodConcrete(name: String, desc: MethodDesc): Method?

    abstract fun getField(name: String, type: Type): Field

    fun getMethod(name: String, desc: String) = getMethod(name, MethodDesc.fromDesc(desc))
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

class ConcreteClass(cn: ClassNode) : Class(cn) {
    override fun getFieldConcrete(name: String, type: Type): Field? = fields.getOrElse(FieldKey(name, type), { superClass?.getFieldConcrete(name, type) })

    override fun getMethodConcrete(name: String, desc: MethodDesc): Method? = methods.getOrElse(MethodKey(name, desc), {
        val uppers = listOf(superClass).plus(interfaces).filterNotNull()
        val res: Method? = uppers
                .mapNotNull { if (it is ConcreteClass) it else null }
                .map { it.getMethodConcrete(name, desc) }
                .firstOrNull() ?: uppers
                .mapNotNull { if (it is OuterClass) it else null }
                .firstOrNull()
                ?.getMethodConcrete(name, desc)
        res
    })

    override fun getField(name: String, type: Type) = fields.getOrElse(FieldKey(name, type), {
        superClass?.getFieldConcrete(name, type) ?: throw UnknownInstance("No field \"$name\" in class $this")
    })

    override fun getMethod(name: String, desc: MethodDesc): Method {
        val methodDesc = MethodKey(name, desc)
        return methods.getOrElse(methodDesc, {
            val `super` = superClass
            getMethodConcrete(name, desc)
                    ?: if (`super` != null && `super` is OuterClass)
                        `super`.getMethod(name, desc)
                    else interfaces.firstOrNull { it is OuterClass }?.getMethod(name, desc)
                            ?: throw UnknownInstance("No method \"$methodDesc\" in $this")
        })
    }

    override fun isAncestor(other: Class): Boolean {
        if (this == other) return true
        else {
            val ancestors = other.getAllAncestors()
            for (it in ancestors) if (isAncestor(it)) return true
        }
        return false
    }
}

class OuterClass(cn: ClassNode) : Class(cn) {
    override fun getFieldConcrete(name: String, type: Type) = getField(name, type)
    override fun getMethodConcrete(name: String, desc: MethodDesc) = getMethod(name, desc)

    override fun getField(name: String, type: Type): Field = fields.getOrPut(FieldKey(name, type), {
        val fn = FieldNode(0, name, type.getAsmDesc(), null, null)
        Field(fn, this)
    })

    override fun getMethod(name: String, desc: MethodDesc): Method {
        val methodDesc = MethodKey(name, desc)
        return methods.getOrPut(methodDesc, {
            val mn = MethodNode()
            mn.name = name
            mn.desc = desc.asmDesc
            Method(mn, this)
        })
    }

    override fun isAncestor(other: Class) = true
}