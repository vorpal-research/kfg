package org.jetbrains.research.kfg.visitor.pass

import org.apache.commons.collections4.map.ReferenceMap
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Node
import org.jetbrains.research.kfg.visitor.NodeVisitor
import org.jetbrains.research.kfg.visitor.Pipeline

@Suppress("UNCHECKED_CAST")
class AnalysisManager(private val cm: ClassManager, private val pipeline: Pipeline) {
    private val visitors = mutableMapOf<Class<out AnalysisVisitor<out AnalysisResult>>, AnalysisVisitor<out AnalysisResult>>()
    private val cache = ReferenceMap<VisitorNodePair, AnalysisResult>()

    fun invalidateAllExcept(visitor: Class<out NodeVisitor>, node: Node) {
        cache.iterator().apply {
            val persistedSet = pipeline.visitorRegistry.getAnalysisPersisted(visitor)
            while (hasNext()) {
                val current = next()
                if (current.key.node === node && !persistedSet.contains(current.key.visitor)) {
                    remove()
                }
            }
        }
    }

    fun <R : AnalysisResult> getAnalysisResult(visitor: Class<out AnalysisVisitor<out AnalysisResult>>, node: Node): R =
            cache.computeIfAbsent(VisitorNodePair(visitor, node)) {
                (getVisitorInstance(visitor) as AnalysisVisitor<*>).analyse(node)
            } as R

    private fun <T : AnalysisVisitor<out AnalysisResult>> getVisitorInstance(visitor: Class<T>): T =
            visitors.computeIfAbsent(visitor) {
                visitor.getConstructor(ClassManager::class.java, Pipeline::class.java)
                    .apply { isAccessible = true }
                    .newInstance(cm, pipeline)
            } as T
}

private data class VisitorNodePair(val visitor: Class<out AnalysisVisitor<out AnalysisResult>>, val node: Node) {
    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }
        if (this === other) {
            return true
        }
        if (other !is VisitorNodePair) {
            return false
        }
        return other.visitor === this.visitor && this.node === other.node
    }

    override fun hashCode(): Int {
        var result = System.identityHashCode(visitor)
        result = 31 * result + System.identityHashCode(node)
        return result
    }
}