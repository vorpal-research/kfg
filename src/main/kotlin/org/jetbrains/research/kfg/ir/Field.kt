package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.parseDesc
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.FieldNode

class Field(cm: ClassManager, val fn: FieldNode, val `class`: Class) : Node(cm, fn.name, fn.access) {
    val type: Type = parseDesc(cm.type, fn.desc)
    val defaultValue: Value? = cm.value.getConstant(fn.value)

    override val asmDesc
        get() = type.asmDesc

    init {
        this.builded = true

        @Suppress("UNCHECKED_CAST") addVisibleAnnotations(fn.visibleAnnotations as List<AnnotationNode>?)
        @Suppress("UNCHECKED_CAST") addInvisibleAnnotations(fn.invisibleAnnotations as List<AnnotationNode>?)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Field

        if (fn != other.fn) return false
        if (`class` != other.`class`) return false
        if (type != other.type) return false
        if (defaultValue != other.defaultValue) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fn.hashCode()
        result = 31 * result + `class`.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + (defaultValue?.hashCode() ?: 0)
        return result
    }

    override fun toString() = "${`class`.fullname}.$name: $type"
}