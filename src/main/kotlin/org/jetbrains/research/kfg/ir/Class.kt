package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.UnknownInstance
import org.jetbrains.research.kfg.defaultHasCode
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.parseMethodDesc
import org.objectweb.asm.tree.*

abstract class Class(val cn: ClassNode) : Node(cn.name.substringAfterLast('/'), cn.access) {
    val packageName: String = cn.name.substringBeforeLast('/')
    val fields = mutableMapOf<String, Field>()
    val methods = mutableMapOf<String, Method>()

    fun init() {
        addVisibleAnnotations(cn.visibleAnnotations as List<AnnotationNode>?)
        addInvisibleAnnotations(cn.invisibleAnnotations as List<AnnotationNode>?)
        cn.fields.forEach {
            it as FieldNode
            fields[it.name] = Field(it, this)
        }
        cn.methods.forEach {
            it as MethodNode
            val pr = parseMethodDesc(it.desc)
            val fullDesc = createMethodDesc(it.name, this, pr.first, pr.second)
            methods[fullDesc] = Method(it, this)
        }
    }

    fun getFullname() = "$packageName/$name"
    override fun getAsmDesc() = "L${getFullname()};"

    fun getSuperClass() = if (cn.superName != null) CM.getByName(cn.name) else null
    fun getInterfaces() = if (cn.interfaces != null) cn.interfaces.map { CM.getByName(it as String) } else listOf()
    fun getOuterClass() = if (cn.outerClass != null) CM.getByName(cn.outerClass) else null
    fun getOuterMethod() = getOuterClass()?.getMethod(cn.outerMethod, cn.outerMethodDesc)
    fun getInnerClasses() = if (cn.innerClasses != null) cn.innerClasses.map { CM.getByName(it as String) } else listOf()

    abstract fun getField(name: String, type: Type): Field
    abstract fun getMethod(name: String, desc: String): Method

    override fun hashCode() = defaultHasCode(name, packageName)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Class
        return this.name == other.name && this.packageName == other.packageName
    }
}

class ConcreteClass(cn: ClassNode) : Class(cn) {
    override fun getField(name: String, type: Type) = fields[name]
            ?: throw UnknownInstance("No field \"$name\" in class ${getFullname()}")

    override fun getMethod(name: String, desc: String): Method {
        val pr = parseMethodDesc(desc)
        val fullDesc = createMethodDesc(name, this, pr.first, pr.second)
        return methods[fullDesc] ?: throw UnknownInstance("No method \"$fullDesc\" in class ${getFullname()}")
    }
}

class OuterClass(cn: ClassNode) : Class(cn) {
    override fun getField(name: String, type: Type): Field = fields.getOrPut(name, {
        val fn = FieldNode(0, name, type.getAsmDesc(), null, null)
        Field(fn, this)
    })

    override fun getMethod(name: String, desc: String): Method {
        val pr = parseMethodDesc(desc)
        val fullDesc = createMethodDesc(name, this, pr.first, pr.second)
        return methods.getOrPut(fullDesc, {
            val mn = MethodNode()
            mn.name = name
            mn.desc = desc
            Method(mn, this)
        })
    }
}