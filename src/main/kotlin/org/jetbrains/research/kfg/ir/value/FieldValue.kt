package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.type.Type

class FieldValue : Value {
    val `object`: Value?
    val field: Field

    constructor(name: String, `class`: Class, type: Type) : super("${`class`.name}.$name", type) {
        this.`object` = null
        this.field = `class`.getField(name, type)
    }

    constructor(name: String, `class`: Class, type: Type, obj: Value) : super("$obj.$name", type) {
        this.`object` = obj
        this.field = `class`.getField(name, type)
    }

    fun isStatic() = `object` == null
}