package org.jetbrains.research.kfg.visitor

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Annotation
import org.jetbrains.research.kfg.ir.Node

interface NodeVisitor {
    val cm: ClassManager
    val instructions get() = cm.instruction
    val types get() = cm.type
    val values get() = cm.value

    fun cleanup()

    fun visit(node: Node) {
        cleanup()
        node.run {
            visibleAnnotations.toTypedArray().forEach { visitVisibleAnnotation(it) }
            invisibleAnnotations.toTypedArray().forEach { visitInvisibleAnnotation(it) }
        }
    }

    fun visitVisibleAnnotation(anno: Annotation) {}
    fun visitInvisibleAnnotation(anno: Annotation) {}
}