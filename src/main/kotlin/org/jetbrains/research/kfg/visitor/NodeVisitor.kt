package org.jetbrains.research.kfg.visitor

import org.jetbrains.research.kfg.ir.Annotation
import org.jetbrains.research.kfg.ir.Node
import org.jetbrains.research.kfg.ir.TypeAnnotation

open class NodeVisitor(val node: Node) {
    open fun visit() {
        node.run {
            visibleAnnotations.forEach { visitVisibleAnnotation(it) }
            invisibleAnnotations.forEach { visitInvisibleAnnotation(it) }
            visibleTypeAnnotations.forEach { visitVisibleTypeAnnotation(it) }
            invisibleTypeAnnotations.forEach { visitInisibleTypeAnnotation(it) }
        }
    }

    open fun visitVisibleAnnotation(anno: Annotation) {}
    open fun visitInvisibleAnnotation(anno: Annotation) {}
    open fun visitVisibleTypeAnnotation(anno: TypeAnnotation) {}
    open fun visitInisibleTypeAnnotation(anno: TypeAnnotation) {}
}