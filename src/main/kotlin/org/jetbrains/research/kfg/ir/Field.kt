package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.VF
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.parseDesc
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.FieldNode

data class Field(val fn: FieldNode, val `class`: Class) : Node(fn.name, fn.access) {
    val type: Type = parseDesc(fn.desc)
    val defaultValue: Value? = VF.getConstant(fn.value)

    override val asmDesc
        get() = type.asmDesc

    init {
        this.builded = true

        @Suppress("UNCHECKED_CAST") addVisibleAnnotations(fn.visibleAnnotations as List<AnnotationNode>?)
        @Suppress("UNCHECKED_CAST") addInvisibleAnnotations(fn.invisibleAnnotations as List<AnnotationNode>?)
    }
}