package org.vorpal.research.kfg.visitor.pass

import org.apache.commons.collections4.map.ReferenceMap
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.Node
import org.vorpal.research.kfg.visitor.NodeVisitor
import org.vorpal.research.kfg.visitor.Pipeline
import java.lang.reflect.ParameterizedType

private typealias ResultClass = Class<out AnalysisResult>
private typealias AnalysisClass = Class<out AnalysisVisitor<out AnalysisResult>>

private fun getLoadedAnalysisVisitors(): Map<ResultClass, List<AnalysisClass>> {
    val processedVisitors = mutableSetOf<AnalysisClass>()
    val visitorToResult = mutableMapOf<AnalysisClass, ResultClass>()
    val resultToVisitors = mutableMapOf<ResultClass, MutableList<AnalysisClass>>()

    fun findResultInterface(visitor: AnalysisClass): ResultClass? =
        visitorToResult[visitor] ?: visitor.genericInterfaces.filterIsInstance<ParameterizedType>()
            .mapNotNull { g ->
                g.actualTypeArguments
                    .filterIsInstance<Class<*>>()
                    .firstOrNull { generic -> AnalysisResult::class.java.isAssignableFrom(generic) }
            }
            .firstOrNull() as ResultClass?

    fun processVisitor(visitor: AnalysisClass) {
        if (processedVisitors.contains(visitor)) {
            return
        }

        val genericInterface = findResultInterface(visitor)

        if (genericInterface != null) {
            processedVisitors.add(visitor)
            visitorToResult[visitor] = genericInterface
            resultToVisitors.getOrPut(genericInterface, ::mutableListOf).add(visitor)
            return
        }

        val genericSuperclass = visitor.genericSuperclass
        if (genericSuperclass is Class<*> &&
            AnalysisVisitor::class.java.isAssignableFrom(genericSuperclass)
        ) {
            processVisitor(genericSuperclass as AnalysisClass)
        }
    }

    val visitors = mutableSetOf<AnalysisClass>()
    AnalysisManager::class.java
        .classLoader
        .definedPackages
        .forEach {
        Reflections(it.name, Scanners.SubTypes)
            .getSubTypesOf(AnalysisVisitor::class.java)
            .forEach { visitor -> visitors.add(visitor) }
    }

    visitors.forEach { processVisitor(it) }

    return resultToVisitors
}

private fun updateLoadedAnalysisVisitors() {
    loadedAnalysisVisitors = getLoadedAnalysisVisitors()
}

private var loadedAnalysisVisitors = getLoadedAnalysisVisitors()

@Suppress("UNCHECKED_CAST")
class AnalysisManager(private val cm: ClassManager, private val pipeline: Pipeline) {
    private val visitors = mutableMapOf<AnalysisClass, AnalysisVisitor<out AnalysisResult>>()
    private val cache = ReferenceMap<VisitorNodePair, AnalysisResult>()

    fun invalidateAllExcept(
        visitor: Class<out NodeVisitor>,
        node: Node,
        persistedAdditional: List<AnalysisClass>
    ) {
        // We don't want to invalidate cache after analysis visitors execution
        if (AnalysisVisitor::class.java.isAssignableFrom(visitor)) {
            return
        }

        cache.iterator().apply {
            val persistedSet = pipeline.visitorRegistry.getAnalysisPersisted(visitor)
            while (hasNext()) {
                val current = next()
                if (current.key.node === node &&
                    !persistedSet.contains(current.key.visitor) &&
                    !persistedAdditional.contains(current.key.visitor)) {
                    remove()
                }
            }
        }
    }

    fun <R : AnalysisResult> getAnalysisResult(visitor: AnalysisClass, node: Node): R =
            cache.computeIfAbsent(VisitorNodePair(visitor, node)) {
                (getVisitorInstance(visitor) as AnalysisVisitor<*>).analyse(node)
            } as R

    fun <R : AnalysisResult> getAnalysisResultByResultClass(resultClass: Class<R>, node: Node): R {
        var visitor = loadedAnalysisVisitors[resultClass]?.first()
        if (visitor == null) {
            updateLoadedAnalysisVisitors()
            visitor = loadedAnalysisVisitors[resultClass]?.first()
                ?: throw IllegalStateException("Could not find analysis visitor for ${resultClass.name} analysis.\n Loaded analysis: $loadedAnalysisVisitors")
        }
        return getAnalysisResult(visitor, node)
    }

    internal fun <T : AnalysisVisitor<out AnalysisResult>> getVisitorInstance(visitor: Class<T>): T =
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