package org.jetbrains.research.kfg.builder.asm

import org.jetbrains.research.kfg.ir.*
import org.jetbrains.research.kfg.ir.Annotation
import org.jetbrains.research.kfg.visitor.ClassVisitor
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode

class ClassBuilder(`class`: Class): ClassVisitor(`class`) {
    val cn = ClassNode()

    init {
        cn.name = `class`.getFullname()
        cn.access = `class`.modifiers
        cn.superName = `class`.superClass?.getFullname()
    }

    override fun visitField(field: Field) {
        val fn = FieldNode(field.modifiers, field.name, field.type.getAsmDesc(), "", field.defaultValue)
        cn.fields.add(fn)
    }

    override fun visitInterface(`interface`: Class) {
        cn.interfaces.add(`interface`.getFullname())
    }

    override fun visitMethod(method: Method) {
        TODO()
    }

    override fun visitVisibleAnnotation(anno: Annotation) {
        val an = AnnotationNode(anno.type.getAsmDesc())
        cn.visibleAnnotations.add(an)
    }

    override fun visitInvisibleAnnotation(anno: Annotation) {
        val an = AnnotationNode(anno.type.getAsmDesc())
        cn.invisibleAnnotations.add(an)
    }

    override fun visitVisibleTypeAnnotation(anno: TypeAnnotation) {
        TODO()
    }

    override fun visitInvisibleTypeAnnotation(anno: TypeAnnotation) {
        TODO()
    }
}