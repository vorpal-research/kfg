package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.type.parseMethodDesc
import org.jetbrains.research.kfg.value.Field

class Class {
    val name: String
    val packageName: String
    var superClass: Class?
    var modifiers: Int
    val fields = mutableListOf<Field>()
    val interfaces = mutableListOf<Class>()
    val methods = mutableMapOf<String, Method>()

    constructor(fullName: String) : this(fullName, null, 0)
    constructor(name: String, packageName: String) : this(name, packageName, null, 0)

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

    fun getMethodByDesc(desc: String) = methods[desc]

    fun getMethod(name: String, desc: String): Method {
        val pr = parseMethodDesc(desc)
        val fullDesc = createMethodDesc(name, this, pr.first, pr.second)
        return methods.getOrPut(fullDesc, { Method(name, this, 0, desc) })
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