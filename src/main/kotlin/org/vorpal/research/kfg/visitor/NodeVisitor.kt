package org.vorpal.research.kfg.visitor

import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Node

interface NodeVisitor {
    val cm: ClassManager
    val pipeline: Pipeline
    val instructions get() = cm.instruction
    val types get() = cm.type
    val values get() = cm.value

    fun cleanup()

    fun visit(node: Node) {
        cleanup()
    }

    fun visitVisibleAnnotation(anno: Annotation) {}
    fun visitInvisibleAnnotation(anno: Annotation) {}

    fun registerPassDependencies() {}
    fun registerAnalysisDependencies() {}
}

interface StandaloneNodeVisitor : NodeVisitor {
    override val pipeline: Pipeline get() = memoizedPipelineStub
}