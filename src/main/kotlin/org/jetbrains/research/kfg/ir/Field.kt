package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.VF
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.parseDesc
import org.jetbrains.research.kfg.util.simpleHash
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.FieldNode

data class Field(val fn: FieldNode, val `class`: Class) : Node(fn.name, fn.access) {
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
}