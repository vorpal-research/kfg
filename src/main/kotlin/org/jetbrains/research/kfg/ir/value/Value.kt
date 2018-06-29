package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kex.util.defaultHashCode
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.Type

abstract class Value(val name: Name, val type: Type) : UsableValue() {
    fun isNameDefined() = name is UndefinedName
    fun hasRealName() = name is StringName
    override fun toString() = name.toString()

    override fun get() = this
}

class Argument(val index: Int, val method: Method, type: Type) : Value(ConstantName("$argPrefix$index"), type) {
    companion object {
        const val argPrefix = "arg\$"
    }

    override fun hashCode() = defaultHashCode(name, type, method)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as Argument
        return this.index == other.index && this.type == other.type && this.method == other.method
    }
}

class ThisRef(type: Type) : Value(ConstantName("this"), type) {
    override fun hashCode() = defaultHashCode(name, type)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as ThisRef
        return this.type == other.type
    }
}