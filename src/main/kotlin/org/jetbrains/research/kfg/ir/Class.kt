package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.value.Field

class Class {
    val name: String
    val packageName: String
    var superClass: Class?
    var modifiers: Int
    val fields = mutableListOf<Field>()
    val interfaces = mutableListOf<Class>()
    val methods = mutableListOf<Method>()

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

    fun getMethod(name: String) = methods.find { it.name == name }

    fun createOrGet(name: String, desc: String): Method {
        val mt = getMethod(name)
        if (mt != null) return mt
        else {
            val new = Method(name, this, 0, desc)
            methods.add(new)
            return new
        }
    }
}