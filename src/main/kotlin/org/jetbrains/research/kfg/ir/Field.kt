package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.VF
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.parseDesc
import org.jetbrains.research.kfg.util.simpleHash
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.FieldNode

class Field(val fn: FieldNode, val `class`: Class) : Node(fn.name, fn.access) {
    val type: Type
    val defaultValue: Value?

    override fun getAsmDesc() = type.getAsmDesc()

    init {
        this.type = parseDesc(fn.desc)
        this.defaultValue = VF.getConstant(fn.value)
        this.builded = true

        @Suppress("UNCHECKED_CAST") addVisibleAnnotations(fn.visibleAnnotations as List<AnnotationNode>?)
        @Suppress("UNCHECKED_CAST") addInvisibleAnnotations(fn.invisibleAnnotations as List<AnnotationNode>?)
    }

    override fun hashCode() = simpleHash(name, `class`, type)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Field
        return this.name == other.name && this.`class` == other.`class` && this.type == other.type
    }
}