package org.jetbrains.research.kfg.value

import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.type.Type

class Field : Value {
    val fieldName: String
    val klass: Class
    val value: Value?
    val isStatic: Boolean

    constructor(name: String, klass: Class, type: Type) : super(type) {
        this.fieldName = name
        this.klass = klass
        this.value = null
        this.isStatic = true
    }

    constructor(name: String, klass: Class, type: Type, obj: Value) : super(type) {
        this.fieldName = name
        this.klass = klass
        this.value = obj
        this.isStatic = false
    }

    override fun getName(): String =
            if (isStatic) "${klass.name}.$fieldName"
            else "$value.$fieldName"
}