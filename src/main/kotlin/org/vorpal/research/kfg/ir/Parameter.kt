package org.vorpal.research.kfg.ir

import org.vorpal.research.kfg.type.Type

class Parameter(
    cm: org.vorpal.research.kfg.ClassManager,
    val index: Int,
    name: String,
    val type: Type,
    modifiers: Modifiers
) : Node(cm, name, modifiers) {
    override val asmDesc = type.asmDesc

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Parameter) return false

        if (index != other.index) return false
        if (type != other.type) return false
        if (asmDesc != other.asmDesc) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + type.hashCode()
        result = 31 * result + asmDesc.hashCode()
        return result
    }
}