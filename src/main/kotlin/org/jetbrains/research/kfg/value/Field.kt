package org.jetbrains.research.kfg.value

import org.jetbrains.research.kfg.type.Type

class Field(val fieldName: String, type: Type, val classType: Type) : Value(type, arrayOf()) {
    override fun getName() = "${classType.getName()}.$fieldName"
}