package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.VF
import org.jetbrains.research.kfg.type.parseMethodDesc
import org.jetbrains.research.kfg.ir.value.Field
import org.jetbrains.research.kfg.type.parseDesc
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

class Class {
    val name: String
    val packageName: String
    val fields = mutableMapOf<String, Field>()
    val interfaces = mutableListOf<Class>()
    val methods = mutableMapOf<String, Method>()
    var superClass: Class?
    var modifiers: Int
    var builded = false

    constructor(fullName: String) : this(fullName, null, -1)
    constructor(name: String, packageName: String) : this(name, packageName, null, -1)

    constructor(fullName: String, superClass: Class?, modifiers: Int) {
        this.name = fullName.substringAfterLast('.')
        this.packageName = fullName.substringBeforeLast('.')
        this.superClass = superClass
        this.modifiers = modifiers
    }

    constructor(name: String, packageName: String, superClass: Class?, modifiers: Int) {
        this.name = name
        this.packageName = packageName
        this.superClass = superClass
        this.modifiers = modifiers
    }

    fun getFullname() = "$packageName.$name"
    private fun getMethodByDesc(desc: String) = methods[desc]

    fun getField(fn: FieldNode) = getField(fn.name, fn.desc, fn.value)

    fun getField(name: String, type: String, `val`: Any?)= fields.getOrPut(name, {
        val irType = parseDesc(type)
        val value = if (`val` != null) VF.getConstant(`val`) else null
        if (value != null) VF.getField(name, this, irType, value)
        else VF.getField(name, this, irType) as Field
    })

    fun getMethod(mn: MethodNode) = getMethod(mn.name, mn.access, mn.desc)

    fun getMethod(name: String, modifiers: Int, desc: String): Method {
        val method = getMethod(name, desc)
        method.modifiers = modifiers
        return method
    }

    fun getMethod(name: String, desc: String): Method {
        val pr = parseMethodDesc(desc)
        val fullDesc = createMethodDesc(name, this, pr.first, pr.second)
        return methods.getOrPut(fullDesc, { Method(name, this, desc) })
    }

    fun isPublic() = isPublic(modifiers)
    fun isPrivate() = isPrivate(modifiers)
    fun isProtected() = isProtected(modifiers)
    fun isFinal() = isFinal(modifiers)
    fun isSuper() = isSuper(modifiers)
    fun isInterface() = isInterface(modifiers)
    fun isAbstract() = isAbstract(modifiers)
    fun isSynthetic() = isSynthetic(modifiers)
    fun isAnnotation() = isAnnotation(modifiers)
    fun isEnum() = isEnum(modifiers)
}