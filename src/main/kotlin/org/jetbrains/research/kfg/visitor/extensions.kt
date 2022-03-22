package org.jetbrains.research.kfg.visitor

import org.jetbrains.research.kfg.ir.Node
import org.jetbrains.research.kfg.visitor.pass.AnalysisResult
import org.jetbrains.research.kfg.visitor.pass.AnalysisVisitor

inline fun <reified Dependency : NodeVisitor> NodeVisitor.addRequiresPass() {
    this.pipeline.visitorRegistry.addRequiresPass(this::class.java, Dependency::class.java)
}

inline fun <reified Dependency : AnalysisVisitor<*>> NodeVisitor.addRequiresAnalysis() {
    this.pipeline.visitorRegistry.addRequiresAnalysis(this::class.java, Dependency::class.java)
}

inline fun <reified Dependency : AnalysisVisitor<*>> NodeVisitor.addPersistedAnalysis() {
    this.pipeline.visitorRegistry.addPersistedAnalysis(this::class.java, Dependency::class.java)
}

inline fun <reified Visitor : NodeVisitor> Pipeline.schedule() {
    this.schedule(Visitor::class.java)
}

inline fun <reified Provider : KfgProvider<DataType>, DataType> NodeVisitor.getProvider(): KfgProvider<DataType> {
    return this.pipeline.visitorRegistry.getProvider(Provider::class.java) as KfgProvider<DataType>
}

inline fun <reified Analysis : AnalysisVisitor<DataType>, DataType : AnalysisResult> NodeVisitor.getAnalysis(node: Node): DataType {
    return this.pipeline.analysisManager.getAnalysisResult(Analysis::class.java, node)
}

fun NodeVisitor.invalidateAnalysisCache(node: Node) {
    this.pipeline.analysisManager.invalidateAllExcept(this::class.java, node)
}

interface VisitorWrapper {
    val wrapped: NodeVisitor
}