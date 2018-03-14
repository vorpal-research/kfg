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
        cn.superName = `class`.getSuperClass()?.getFullname()
        cn.version = `class`.cn.version
        cn.signature = `class`.cn.signature
        cn.outerClass = `class`.getOuterClass()?.getFullname()
        cn.outerMethod = `class`.getOuterMethod()?.name
        cn.outerMethodDesc = `class`.getOuterMethod()?.getAsmDesc()
    }

    override fun visitField(field: Field) {
        val fn = FieldNode(field.modifiers, field.name, field.type.getAsmDesc(), field.fn.signature, field.defaultValue)
        cn.fields.add(fn)
    }

    override fun visitInterface(`interface`: Class) {
        cn.interfaces.add(`interface`.getFullname())
    }

    override fun visitMethod(method: Method) {
        val builder = MethodBuilder(method)
        builder.visit()
        cn.methods.add(builder.mn)
    }

    override fun visitVisibleAnnotation(anno: Annotation) {
        if (cn.visibleAnnotations == null) cn.visibleAnnotations = mutableListOf<AnnotationNode>()
        val an = AnnotationNode(anno.type.getAsmDesc())
        cn.visibleAnnotations.add(an)
    }

    override fun visitInvisibleAnnotation(anno: Annotation) {
        if (cn.invisibleAnnotations == null) cn.invisibleAnnotations = mutableListOf<AnnotationNode>()
        val an = AnnotationNode(anno.type.getAsmDesc())
        cn.invisibleAnnotations.add(an)
    }
}