package org.jetbrains.research.kfg.visitor.pass

import org.jetbrains.research.kfg.ir.Node
import org.jetbrains.research.kfg.visitor.NodeVisitor

interface AnalysisResult
interface AnalysisVisitor<T : AnalysisResult> : NodeVisitor {
    fun analyse(node: Node): T
    override fun cleanup() {}
}