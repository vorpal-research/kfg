package org.jetbrains.research.kfg.visitor

import org.jetbrains.research.kfg.ir.Annotation
import org.jetbrains.research.kfg.ir.Node

interface NodeVisitor {
    fun visit(node: Node) {
        node.run {
            visibleAnnotations.toTypedArray().forEach { visitVisibleAnnotation(it) }
            invisibleAnnotations.toTypedArray().forEach { visitInvisibleAnnotation(it) }
        }
    }

    fun visitVisibleAnnotation(anno: Annotation) {}
    fun visitInvisibleAnnotation(anno: Annotation) {}
}