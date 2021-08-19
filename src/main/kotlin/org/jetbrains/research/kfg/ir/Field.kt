package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.parseDesc
import org.objectweb.asm.tree.FieldNode

class Field : Node {
    val klass: Class
    internal val fn: FieldNode
    val type: Type
    var defaultValue: Value?

    constructor(cm: ClassManager, klass: Class, fn: FieldNode) : super(cm, fn.name, fn.access) {
        this.fn = fn
        this.klass = klass
        this.type = parseDesc(cm.type, fn.desc)
        this.defaultValue = cm.value.getConstant(fn.value)
    }

    constructor(cm: ClassManager, klass: Class, name: String, type: Type, modifiers: Int = 0) :
            super(cm, name, modifiers) {
        this.fn = FieldNode(modifiers, name, type.asmDesc, null, null)
        this.klass = klass
        this.type = type
        this.defaultValue = null
    }

    override val asmDesc
        get() = type.asmDesc

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Field

        if (klass != other.klass) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fn.hashCode()
        result = 31 * result + klass.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }

    override fun toString() = "${klass.fullName}.$name: $type"
}