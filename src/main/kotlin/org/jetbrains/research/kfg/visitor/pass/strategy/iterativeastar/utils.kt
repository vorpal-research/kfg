package org.jetbrains.research.kfg.visitor.pass.strategy.iterativeastar

import org.jetbrains.research.kfg.visitor.NodeVisitor
import org.jetbrains.research.kfg.visitor.VisitorRegistry

internal fun SearchNode.openNodes(
    currentIteration: IterationSearchNode,
    open: Collection<SearchNode>
): List<SearchNode> = this.previousIteration.passesLeft
    .filter { it.isAvailableFrom(this) }
    .map {
        val availableAnalysis = this.availableAnalysis.toMutableSet()
        val analysisComputed = this.analysisComputed + it.updateAnalysis(availableAnalysis)

        SearchNode(
            previousIteration = currentIteration,
            passOrder = this.passOrder.toMutableList().apply { add(it) },
            availableAnalysis = availableAnalysis,
            closedPasses = this.closedPasses.toMutableSet().apply { add(it.name) },
            analysisComputed = analysisComputed,
            openNodesCount = open.size
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

internal data class VisitorWrapper(val nodeVisitor: NodeVisitor) {
    val name = nodeVisitor.getName()

    val requiredPasses = nodeVisitor.getRequiredPasses()
    val requiredPassesSet = nodeVisitor.getRequiredPasses().toSet()

    val requiredAnalysis = nodeVisitor.getRequiredAnalysisVisitors()
    val requiredAnalysisSet = nodeVisitor.getRequiredAnalysisVisitors().toSet()

    val persistedAnalysis = nodeVisitor.getPersistedAnalysisVisitors()
    val persistedAnalysisSet = nodeVisitor.getPersistedAnalysisVisitors().toSet()

    val availableAnalysisAfterPass = nodeVisitor.getRequiredAnalysisVisitors()
        .filter { nodeVisitor.getPersistedAnalysisVisitors().contains(it) }

    fun isAvailableFrom(searchNode: SearchNode) =
        !searchNode.closedPasses.contains(name) && !searchNode.previousIteration.closedPasses.contains(name) &&
                requiredPasses.all {
                    searchNode.closedPasses.contains(it) || searchNode.previousIteration.closedPasses.contains(it)
                }

    fun isAvailableFrom(iterationSearchNode: IterationSearchNode) =
        !iterationSearchNode.closedPasses.contains(name) &&
                iterationSearchNode.closedPasses.containsAll(requiredPasses)

    fun updateAnalysis(availableAnalysis: MutableSet<String>): Int {
        val computedAnalysisCount = requiredAnalysis.count { !availableAnalysis.contains(it) }

        availableAnalysis.removeIf { !persistedAnalysisSet.contains(it) }
        availableAnalysis.addAll(availableAnalysisAfterPass)

        return computedAnalysisCount
    }
}

internal data class IterationSearchNode(
    val passOrder: List<VisitorWrapper>,
    val availableAnalysis: Set<String>,
    val closedPasses: Set<String>,
    val passesLeft: List<VisitorWrapper>,
    val analysisComputed: Int
)

internal data class SearchNode(
    val previousIteration: IterationSearchNode,
    val passOrder: List<VisitorWrapper>,
    val availableAnalysis: Set<String>,
    val closedPasses: Set<String>,
    val analysisComputed: Int,
    val openNodesCount: Int
) {
    val depth = closedPasses.size // depth inside the iteration
    val evaluation = evaluate()

    private fun evaluate(): Float {
        val visitorAnalysisRatio = VisitorRegistry.getAnalysisCount().toFloat() / VisitorRegistry.getVisitorsCount()
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
            .filter { !closedPasses.contains(it.name) }
            .sumOf { it.requiredAnalysis.size }
    }
}