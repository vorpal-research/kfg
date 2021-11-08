package org.jetbrains.research.kfg.visitor.pass

import org.apache.commons.collections4.map.ReferenceMap
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Node
import org.jetbrains.research.kfg.visitor.Pipeline

private fun Node.equalsTo(other: Node) = this === other

@Suppress("UNCHECKED_CAST")
class AnalysisManager(private val cm: ClassManager, private val pipeline: Pipeline) {
    private val visitors = mutableMapOf<Class<AnalysisVisitor<*>>, AnalysisVisitor<*>>()
    private val cache = ReferenceMap<VisitorNodePair, AnalysisResult>()

    fun <T : AnalysisVisitor<*>> invalidateAllExcept(persisted: List<Class<T>>, node: Node) {
        cache.iterator().apply {
            val persistedSet = persisted.toSet()
            while (hasNext()) {
                val current = next()
                if (current.key.node.equalsTo(node) &&
                        !persistedSet.contains(current.key.visitor as Class<T>)) {
                    remove()
                }
            }
        }
    }

    fun <R : AnalysisResult, T : AnalysisVisitor<R>> getAnalysisResult(visitorClass: Class<T>, node: Node): R =
            cache.computeIfAbsent(VisitorNodePair(visitorClass as Class<AnalysisVisitor<AnalysisResult>>, node)) {
                getVisitorInstance(visitorClass).analyse(node)
            } as R

    private fun <T : AnalysisVisitor<*>> getVisitorInstance(analysisVisitorClass: Class<T>): T =
            visitors.computeIfAbsent(analysisVisitorClass as Class<AnalysisVisitor<*>>) {
                analysisVisitorClass.getConstructor(ClassManager::class.java, Pipeline::class.java)
                        .apply { isAccessible = true }
                        .newInstance(cm, pipeline) as T
            } as T
}

private data class VisitorNodePair(val visitor: Class<AnalysisVisitor<AnalysisResult>>, val node: Node) {
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
        return other.visitor === this.visitor && this.node.equalsTo(other.node)
    }

    override fun hashCode(): Int {
        var result = System.identityHashCode(visitor)
        result = 31 * result + System.identityHashCode(node)
        return result
    }
}