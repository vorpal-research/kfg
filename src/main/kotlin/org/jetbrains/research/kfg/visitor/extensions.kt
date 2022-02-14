package org.jetbrains.research.kfg.visitor

import org.jetbrains.research.kfg.visitor.pass.AnalysisVisitor

inline fun <reified Dependency : NodeVisitor> NodeVisitor.addRequiresPass() {
    this.pipeline.visitorRegistry.addRequiresPass(this::class.java, Dependency::class.java)
}

inline fun <reified Dependency : AnalysisVisitor<*>> NodeVisitor.addRequiresAnalysis() {
    this.pipeline.visitorRegistry.addRequiresAnalysis(this::class.java, Dependency::class.java)
}

inline fun <reified Dependency : AnalysisVisitor<*>> NodeVisitor.addInvalidatesAnalysis() {
    this.pipeline.visitorRegistry.addInvalidatesAnalysis(this::class.java, Dependency::class.java)
}

inline fun <reified Visitor : NodeVisitor> Pipeline.schedule() {
    this.schedule(Visitor::class.java)
}

inline fun <reified Provider : KfgProvider<DataType>, DataType> VisitorRegistry.getProvider(): KfgProvider<DataType> {
    return this.getProvider(Provider::class.java) as KfgProvider<DataType>
}