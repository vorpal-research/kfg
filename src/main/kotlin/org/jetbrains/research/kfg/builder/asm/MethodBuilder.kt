package org.jetbrains.research.kfg.builder.asm

import org.jetbrains.research.kfg.ir.*
import org.jetbrains.research.kfg.ir.Annotation
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.ParameterNode

class MethodBuilder(method: Method) : MethodVisitor(method) {
    val mn = MethodNode()
    val labels = mutableMapOf<BasicBlock, LabelNode>()

    init {
        mn.access = method.modifiers
        mn.name = method.name
        mn.desc = method.getAsmDesc()
        mn.exceptions.addAll(method.exceptions.map { it.getAsmDesc() })
        mn.visibleParameterAnnotations = arrayOfNulls(method.argTypes.size)
        mn.invisibleParameterAnnotations = arrayOfNulls(method.argTypes.size)
    }

    override fun visitVisibleAnnotation(anno: Annotation) {
        val an = AnnotationNode(anno.type.getAsmDesc())
        mn.visibleAnnotations.add(an)
    }

    override fun visitInvisibleAnnotation(anno: Annotation) {
        val an = AnnotationNode(anno.type.getAsmDesc())
        mn.invisibleAnnotations.add(an)
    }

    override fun visitVisibleTypeAnnotation(anno: TypeAnnotation) {
        TODO()
    }

    override fun visitInvisibleTypeAnnotation(anno: TypeAnnotation) {
        TODO()
    }

    override fun visitParameter(parameter: Parameter) {
        val pn = ParameterNode(parameter.name, parameter.modifiers)
        mn.parameters.add(pn)
        mn.visibleParameterAnnotations[parameter.indx] = parameter.visibleAnnotations.map { AnnotationNode(it.type.getAsmDesc()) }.toList()
        mn.invisibleParameterAnnotations[parameter.indx] = parameter.invisibleAnnotations.map { AnnotationNode(it.type.getAsmDesc()) }.toList()
    }

    override fun visitBasicBlock(bb: BasicBlock) {
        labels[bb] = LabelNode()
    }
}