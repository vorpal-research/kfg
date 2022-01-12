package org.jetbrains.research.kfg.visitor.pass.strategy.iterativeastar

import org.jetbrains.research.kfg.visitor.NodeVisitor
import org.jetbrains.research.kfg.visitor.Pipeline
import org.jetbrains.research.kfg.visitor.pass.strategy.IteratedPassOrder
import org.jetbrains.research.kfg.visitor.pass.strategy.PassOrder
import org.jetbrains.research.kfg.visitor.pass.strategy.PassStrategy
import java.util.*

// Use iterative way - limit the depth by 10 (for example) and try our best at this depth
private const val ITERATIVE_DEPTH = 15

class IterativeAStarPassStrategy : PassStrategy {
    override fun isParallelSupported() = false

    override fun createPassOrder(pipeline: Pipeline, parallel: Boolean): PassOrder {
        val allPasses = pipeline.getPasses().map { VisitorWrapper(it) }

        var currentIteration = IterationSearchNode(
            passOrder = emptyList(),
            availableAnalysis = emptySet(),
            closedPasses = emptySet(),
            passesLeft =  allPasses,
            analysisComputed = 0
        )

        while (currentIteration.passesLeft.isNotEmpty()) {
            val openSearchNodes = currentIteration.passesLeft
                .filter { it.isAvailableFrom(currentIteration) }
                .map {
                    val availableAnalysis = currentIteration.availableAnalysis.toMutableSet()
                    val analysisComputed = it.updateAnalysis(availableAnalysis)

                    SearchNode(
                        previousIteration = currentIteration,
                        passOrder = listOf(it),
                        availableAnalysis = availableAnalysis,
                        closedPasses = setOf(it.name),
                        analysisComputed = analysisComputed,
                        openNodesCount = 0
                    )
                }

            val open = PriorityQueue<SearchNode> { first, second -> first.evaluation.compareTo(second.evaluation) }
            open.addAll(openSearchNodes)

            var newIteration: IterationSearchNode? = null
            while (newIteration == null) {
                val currentNode = open.poll()

                val openedNodes = currentNode.previousIteration.passesLeft
                    .filter { it.isAvailableFrom(currentNode) }
                    .map {
                        val availableAnalysis = currentNode.availableAnalysis.toMutableSet()
                        val analysisComputed = currentNode.analysisComputed + it.updateAnalysis(availableAnalysis)

                        SearchNode(
                            previousIteration = currentIteration,
                            passOrder = currentNode.passOrder.toMutableList().apply { add(it) },
                            availableAnalysis = availableAnalysis,
                            closedPasses = currentNode.closedPasses.toMutableSet().apply { add(it.name) },
                            analysisComputed = analysisComputed,
                            openNodesCount = open.size
                        )
                    }

                for (opened in openedNodes) {
                    if (opened.depth == ITERATIVE_DEPTH ||
                        opened.closedPasses.size == opened.previousIteration.passesLeft.size
                    ) {
                        newIteration = IterationSearchNode(
                            passOrder = currentIteration.passOrder.toMutableList().apply { addAll(opened.passOrder) },
                            availableAnalysis = opened.availableAnalysis,
                            closedPasses = currentIteration.closedPasses.toMutableSet()
                                .apply { addAll(opened.closedPasses) },
                            passesLeft = currentIteration.passesLeft.filter { !opened.closedPasses.contains(it.name) },
                            analysisComputed = opened.analysisComputed
                        )
                        open.clear()
                        break
                    }

                    open.addAll(openedNodes)
                }
            }

            currentIteration = newIteration
        }

        return IteratedPassOrder(currentIteration.passOrder.map { it.nodeVisitor }.iterator())
    }
}

data class VisitorWrapper(val nodeVisitor: NodeVisitor) {
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

data class IterationSearchNode(
    val passOrder: List<VisitorWrapper>,
    val availableAnalysis: Set<String>,
    val closedPasses: Set<String>,
    val passesLeft: List<VisitorWrapper>,
    val analysisComputed: Int
)

data class SearchNode(
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
        val passesLeft = ITERATIVE_DEPTH - depth
        val analysisComputed = analysisComputed
        val analysisLeft = estimateLeftAnalysis()
        val analysisCacheSize = availableAnalysis.size

        return analysisComputed + analysisLeft * 0.6f - analysisCacheSize - (openNodesCount * 0.0002 * closedPasses.size).toFloat()
    }

    private fun estimateLeftAnalysis(): Int {
        return previousIteration.passesLeft
            .filter { !closedPasses.contains(it.name) }
            .sumOf { it.requiredAnalysis.size }
    }
}