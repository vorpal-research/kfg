package org.jetbrains.research.kfg.visitor

import org.jetbrains.research.kfg.ir.Annotation
import org.jetbrains.research.kfg.ir.Node

open class NodeVisitor(val node: Node) {
    open fun visit() {
        node.run {
            visibleAnnotations.forEach { visitVisibleAnnotation(it) }
            invisibleAnnotations.forEach { visitInvisibleAnnotation(it) }
        }
    }

    open fun visitVisibleAnnotation(anno: Annotation) {}
    open fun visitInvisibleAnnotation(anno: Annotation) {}
}