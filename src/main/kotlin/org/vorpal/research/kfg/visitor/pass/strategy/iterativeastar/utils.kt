package org.vorpal.research.kfg.visitor.pass.strategy.iterativeastar

import kotlinx.collections.immutable.*
import org.vorpal.research.kfg.visitor.NodeVisitor
import org.vorpal.research.kfg.visitor.InternalVisitorRegistry
import org.vorpal.research.kfg.visitor.pass.AnalysisResult
import org.vorpal.research.kfg.visitor.pass.AnalysisVisitor

internal fun SearchNode.openNodes(
    currentIteration: IterationSearchNode,
    open: Collection<SearchNode>,
    visitorRegistry: InternalVisitorRegistry
): List<SearchNode> = this.previousIteration.passesLeft
    .filter { it.isAvailableFrom(this) }
    .map {
        val availableAnalysis = this.availableAnalysis.toMutableSet()
        val analysisComputed = this.analysisComputed + it.updateAnalysis(availableAnalysis)

        SearchNode(
            previousIteration = currentIteration,
            passOrder = this.passOrder + it,
            availableAnalysis = availableAnalysis,
            closedPasses = this.closedPasses + it.nodeVisitor::class.java,
            analysisComputed = analysisComputed,
            openNodesCount = open.size,
            visitorRegistry
        )
    }

internal fun SearchNode.getSuitableNode(openedNodes: List<SearchNode>, iterativeDepth: Int) =
    openedNodes.toMutableList()
        .apply { add(this@getSuitableNode) }
        .filter { it.depth == iterativeDepth ||
                it.closedPasses.size == it.previousIteration.passesLeft.size }
        .minByOrNull { it.analysisComputed }

internal fun <T> Iterable<T>.merge(other: Iterable<T>): Iterator<T> {
    val iterator1 = this.iterator()
    val iterator2 = other.iterator()
    return object : Iterator<T> {
        override fun hasNext(): Boolean =
            iterator1.hasNext() || iterator2.hasNext()

        override fun next(): T =
            if (iterator1.hasNext()) iterator1.next() else iterator2.next()
    }
}

internal data class VisitorWrapper(val nodeVisitor: NodeVisitor, private val visitorRegistry: InternalVisitorRegistry) {
    val requiredPasses = visitorRegistry.getVisitorDependencies(nodeVisitor::class.java)

    val requiredAnalysis = visitorRegistry.getAnalysisDependencies(nodeVisitor::class.java)

    val persistedAnalysisSet = visitorRegistry.getAnalysisPersisted(nodeVisitor::class.java)

    val availableAnalysisAfterPass = visitorRegistry.getAnalysisDependencies(nodeVisitor::class.java)
        .filter { visitorRegistry.getAnalysisPersisted(nodeVisitor::class.java).contains(it) }

    fun isAvailableFrom(searchNode: SearchNode) =
        !searchNode.closedPasses.contains(nodeVisitor::class.java) &&
                !searchNode.previousIteration.closedPasses.contains(nodeVisitor::class.java) &&
                requiredPasses.all {
                    searchNode.closedPasses.contains(it) || searchNode.previousIteration.closedPasses.contains(it)
                }

    fun isAvailableFrom(iterationSearchNode: IterationSearchNode) =
        !iterationSearchNode.closedPasses.contains(nodeVisitor::class.java) &&
                iterationSearchNode.closedPasses.containsAll(requiredPasses)

    fun updateAnalysis(availableAnalysis: MutableSet<Class<out AnalysisVisitor<out AnalysisResult>>>): Int {
        val computedAnalysisCount = requiredAnalysis.count { !availableAnalysis.contains(it) }

        availableAnalysis.removeIf { !persistedAnalysisSet.contains(it) }
        availableAnalysis.addAll(availableAnalysisAfterPass)

        return computedAnalysisCount
    }
}

internal data class IterationSearchNode(
    val passOrder: PersistentList<VisitorWrapper>,
    val availableAnalysis: Set<Class<out AnalysisVisitor<out AnalysisResult>>>,
    val closedPasses: PersistentSet<Class<out NodeVisitor>>,
    val passesLeft: List<VisitorWrapper>,
    val analysisComputed: Int
)

internal data class SearchNode(
    val previousIteration: IterationSearchNode,
    val passOrder: PersistentList<VisitorWrapper>,
    val availableAnalysis: Set<Class<out AnalysisVisitor<out AnalysisResult>>>,
    val closedPasses: PersistentSet<Class<out NodeVisitor>>,
    val analysisComputed: Int,
    val openNodesCount: Int,
    val visitorRegistry: InternalVisitorRegistry
) {
    val depth = closedPasses.size // depth inside the iteration
    val evaluation = evaluate()

    private fun evaluate(): Float {
        val visitorAnalysisRatio = visitorRegistry.analysisCount.toFloat() / visitorRegistry.visitorsCount
        val varSqr = if (visitorAnalysisRatio > 1) 1.0f else visitorAnalysisRatio * visitorAnalysisRatio
        val passesLeft = previousIteration.passesLeft.size - closedPasses.size
        val analysisComputed = analysisComputed
        val analysisLeft = estimateLeftAnalysis()
        val analysisCacheSize = availableAnalysis.size

        return analysisComputed + analysisLeft * 0.6f -
                analysisCacheSize - (openNodesCount * 0.0002 * closedPasses.size * varSqr).toFloat()
    }

    private fun estimateLeftAnalysis(): Int {
        return previousIteration.passesLeft
            .filter { !closedPasses.contains(it.nodeVisitor::class.java) }
            .sumOf { it.requiredAnalysis.size }
    }
}