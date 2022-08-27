package org.vorpal.research.kfg.visitor.pass

import org.vorpal.research.kfg.ir.Node
import org.vorpal.research.kfg.visitor.NodeVisitor

interface AnalysisResult
interface AnalysisVisitor<T : AnalysisResult> : NodeVisitor {
    override fun visit(node: Node) {
        cleanup()
        analyse(node)
    }

    fun analyse(node: Node): T
    override fun cleanup() {}
}