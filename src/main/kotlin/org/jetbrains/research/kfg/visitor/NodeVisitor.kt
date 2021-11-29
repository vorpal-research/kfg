package org.jetbrains.research.kfg.visitor

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Node
import org.jetbrains.research.kfg.visitor.pass.AnalysisVisitor

interface NodeVisitor {
    val cm: ClassManager
    val pipeline: Pipeline
    val instructions get() = cm.instruction
    val types get() = cm.type
    val values get() = cm.value

    fun getName(): String = this.javaClass.name
    fun cleanup()

    fun visit(node: Node) {
        cleanup()
    }

    fun visitVisibleAnnotation(anno: Annotation) {}
    fun visitInvisibleAnnotation(anno: Annotation) {}

    fun getRequiredAnalysisVisitors(): List<String> = emptyList()
    fun getPersistedAnalysisVisitors(): List<String> = emptyList()

    fun getRequiredPasses(): List<String> = emptyList()
}