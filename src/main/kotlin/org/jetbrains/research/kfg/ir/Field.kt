package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.VF
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.parseDesc
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.TypeAnnotationNode

class Field : Node {
    val `class`: Class
    val type: Type
    val defaultValue: Value?

    constructor(fn: FieldNode, `class`: Class)
            : this(fn.name, `class`, parseDesc(fn.desc), fn.access, VF.getConstant(fn.value)) {
        this.builded = true

        addVisibleAnnotations(fn.visibleAnnotations as List<AnnotationNode>?)
        addInvisibleAnnotations(fn.invisibleAnnotations as List<AnnotationNode>?)
        addVisibleTypeAnnotations(fn.visibleTypeAnnotations as List<TypeAnnotationNode>?)
        addInvisibleTypeAnnotations(fn.invisibleTypeAnnotations as List<TypeAnnotationNode>?)
    }

    constructor(name: String, `class`: Class, type: Type) : this(name, `class`, type, null)
    constructor(name: String, `class`: Class, type: Type, defalut: Value?) : this(name, `class`, type, 0, defalut)

    constructor(name: String, `class`: Class, type: Type, modifiers: Int, defalut: Value?) : super(name, modifiers) {
        this.`class` = `class`
        this.type = type
        this.defaultValue = defalut
    }

    override fun getAsmDesc() = type.getAsmDesc()
}