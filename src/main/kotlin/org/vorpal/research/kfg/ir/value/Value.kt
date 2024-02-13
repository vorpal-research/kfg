package org.vorpal.research.kfg.ir.value

import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.type.Type

abstract class Value(val name: Name, val type: Type) : UsableValue() {
    val isNameDefined: Boolean
        get() = name !is UndefinedName

    val hasRealName get() = name is StringName
    override fun toString() = name.toString()

    override fun get() = this
}

class Argument(val index: Int, val method: Method, type: Type) : Value(ConstantName("$ARG_PREFIX$index"), type) {
    companion object {
        const val ARG_PREFIX = "arg\$"
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + method.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as Argument
        return this.index == other.index && this.type == other.type && this.method == other.method
    }
}

class ThisRef(type: Type) : Value(ConstantName("this"), type) {
    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as ThisRef
        return this.type == other.type
    }
}
