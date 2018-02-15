package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.value.Field

class Class {
    val name: String
    val packageName: String
    val superClass: Class?
    val modifiers: Int
    val fields = mutableListOf<Field>()
    val interfaces = mutableListOf<Class>()
    val methods = mutableListOf<Method>()

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
}