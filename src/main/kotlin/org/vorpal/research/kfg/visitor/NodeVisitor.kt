package org.vorpal.research.kfg.visitor

import org.vorpal.research.kfg.ir.Node

interface NodeVisitor {
    val cm: org.vorpal.research.kfg.ClassManager
    val instructions get() = cm.instruction
    val types get() = cm.type
    val values get() = cm.value

    fun cleanup()

    fun visit(node: Node) {
        cleanup()
    }

    fun visitVisibleAnnotation(anno: Annotation) {}
    fun visitInvisibleAnnotation(anno: Annotation) {}
}