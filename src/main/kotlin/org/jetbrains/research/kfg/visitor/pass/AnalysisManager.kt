package org.jetbrains.research.kfg.visitor.pass

import org.apache.commons.collections4.map.ReferenceMap
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Node
import org.jetbrains.research.kfg.visitor.Pipeline
import org.jetbrains.research.kfg.visitor.VisitorRegistry

private fun Node.equalsTo(other: Node) = this === other

@Suppress("UNCHECKED_CAST")
class AnalysisManager(private val cm: ClassManager, private val pipeline: Pipeline) {
    private val visitors = mutableMapOf<String, AnalysisVisitor<*>>()
    private val cache = ReferenceMap<VisitorNodePair, AnalysisResult>()

    fun invalidateAllExcept(persisted: List<String>, node: Node) {
        cache.iterator().apply {
            val persistedSet = persisted.toSet()
            while (hasNext()) {
                val current = next()
                if (current.key.node.equalsTo(node) &&
                        !persistedSet.contains(current.key.name)) {
                    remove()
                }
            }
        }
    }

    fun <R : AnalysisResult> getAnalysisResult(name: String, node: Node): R =
            cache.computeIfAbsent(VisitorNodePair(name, node)) {
                (getVisitorInstance(name) as AnalysisVisitor<*>).analyse(node)
            } as R

    private fun <T : AnalysisVisitor<*>> getVisitorInstance(name: String): T =
            visitors.computeIfAbsent(name) {
                VisitorRegistry.getAnalysis(name)!!.invoke(cm, pipeline) as T
            } as T
}

private data class VisitorNodePair(val name: String, val node: Node) {
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
        return other.name == this.name && this.node.equalsTo(other.node)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + System.identityHashCode(node)
        return result
    }
}