package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.UnknownInstance
import org.jetbrains.research.kfg.util.defaultHasCode
import org.jetbrains.research.kfg.type.Type
import org.objectweb.asm.tree.*

data class MethodDesc(val name: String, val desc: String)

abstract class Class(val cn: ClassNode) : Node(cn.name.substringAfterLast('/'), cn.access) {
    val packageName: String = cn.name.substringBeforeLast('/')
    val fields = mutableMapOf<String, Field>()
    val methods = mutableMapOf<MethodDesc, Method>()

    fun init() {
        addVisibleAnnotations(cn.visibleAnnotations as List<AnnotationNode>?)
        addInvisibleAnnotations(cn.invisibleAnnotations as List<AnnotationNode>?)
        cn.fields.forEach {
            it as FieldNode
            fields[it.name] = Field(it, this)
        }
        cn.methods.forEach {
            it as MethodNode
            methods[MethodDesc(it.name, it.desc)] = Method(it, this)
        }
    }

    fun getFullname() = "$packageName/$name"
    override fun getAsmDesc() = "L${getFullname()};"

    fun getSuperClass() = if (cn.superName != null) CM.getByName(cn.superName) else null
    fun getInterfaces() = if (cn.interfaces != null) cn.interfaces.map { CM.getByName(it as String) } else listOf()
    fun getOuterClass() = if (cn.outerClass != null) CM.getByName(cn.outerClass) else null
    fun getOuterMethod() = if (cn.outerMethod != null) getOuterClass()?.getMethod(cn.outerMethod, cn.outerMethodDesc) else null
    fun getInnerClasses() = if (cn.innerClasses != null) cn.innerClasses.map { CM.getByName(it as String) } else listOf()

    abstract fun getFieldConcrete(name: String, type: Type): Field?
    abstract fun getMethodConcrete(name: String, desc: String): Method?

    abstract fun getField(name: String, type: Type): Field
    abstract fun getMethod(name: String, desc: String): Method

    override fun toString() = getFullname()
    override fun hashCode() = defaultHasCode(name, packageName)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Class
        return this.name == other.name && this.packageName == other.packageName
    }
}

class ConcreteClass(cn: ClassNode) : Class(cn) {
    override fun getFieldConcrete(name: String, type: Type): Field? = fields.getOrElse(name, { getSuperClass()?.getFieldConcrete(name, type) })

    override fun getMethodConcrete(name: String, desc: String): Method? = methods.getOrElse(MethodDesc(name, desc), {
        val uppers = listOf(getSuperClass()).plus(getInterfaces()).filterNotNull()
        val res: Method? = uppers
                .mapNotNull { if (it is ConcreteClass) it else null }
                .map { it.getMethodConcrete(name, desc) }
                .firstOrNull() ?: uppers
                .mapNotNull { if (it is OuterClass) it else null }
                .firstOrNull()
                ?.getMethodConcrete(name, desc)
        res
    })

    override fun getField(name: String, type: Type) = fields.getOrElse(name, {
        getSuperClass()?.getFieldConcrete(name, type) ?: throw UnknownInstance("No field \"$name\" in class $this")
    })

    override fun getMethod(name: String, desc: String): Method {
        val methodDesc = MethodDesc(name, desc)
        return methods.getOrElse(methodDesc, {
            val `super` = getSuperClass()
            getMethodConcrete(name, desc)
                    ?: if (`super` != null && `super` is OuterClass)
                        `super`.getMethod(name, desc)
                    else getInterfaces().firstOrNull { it is OuterClass }?.getMethod(name, desc)
                            ?: throw UnknownInstance("No method \"$methodDesc\" in $this")
        })
    }
}

class OuterClass(cn: ClassNode) : Class(cn) {
    override fun getFieldConcrete(name: String, type: Type) = getField(name, type)
    override fun getMethodConcrete(name: String, desc: String) = getMethod(name, desc)

    override fun getField(name: String, type: Type): Field = fields.getOrPut(name, {
        val fn = FieldNode(0, name, type.getAsmDesc(), null, null)
        Field(fn, this)
    })

    override fun getMethod(name: String, desc: String): Method {
        val methodDesc = MethodDesc(name, desc)
        return methods.getOrPut(methodDesc, {
            val mn = MethodNode()
            mn.name = name
            mn.desc = desc
            Method(mn, this)
        })
    }
}