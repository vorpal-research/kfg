package org.vorpal.research.kfg.visitor.pass.strategy.iterativeastar

import org.vorpal.research.kfg.visitor.Pipeline
import org.vorpal.research.kfg.visitor.pass.strategy.IteratedPassOrder
import org.vorpal.research.kfg.visitor.pass.strategy.PassOrder
import org.vorpal.research.kfg.visitor.pass.strategy.PassStrategy
import java.util.*

// Use iterative way - limit the depth by 10 (for example) and try our best at this dept
// Make step only to depth 1
private const val ITERATIVE_DEPTH = 10

class IterativeAStarPlusPassStrategy : PassStrategy {
    override fun isParallelSupported() = false

    override fun createPassOrder(pipeline: Pipeline, parallel: Boolean): PassOrder {
        val allPasses = pipeline.getPasses().map { VisitorWrapper(it, pipeline.visitorRegistry) }

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
                    val analysisComputed = currentIteration.analysisComputed + it.updateAnalysis(availableAnalysis)

                    SearchNode(
                        previousIteration = currentIteration,
                        passOrder = listOf(it),
                        availableAnalysis = availableAnalysis,
                        closedPasses = setOf(it.nodeVisitor::class.java),
                        analysisComputed = analysisComputed,
                        openNodesCount = 0,
                        pipeline.visitorRegistry
                    )
                }

            val open = PriorityQueue<SearchNode> { first, second -> first.evaluation.compareTo(second.evaluation) }
            open.addAll(openSearchNodes)

            var newIteration: IterationSearchNode? = null
            while (newIteration == null) {
                val currentNode = open.poll()

                if (currentNode == null) {
                    println()
                }

                val openedNodes = currentNode.openNodes(currentIteration, open, pipeline.visitorRegistry)

                val suitableForNextIteration = currentNode.getSuitableNode(openedNodes, ITERATIVE_DEPTH)

                if (suitableForNextIteration != null) {
                    open.clear()

                    val first = suitableForNextIteration.passOrder.first()

                    val availableAnalysis = currentIteration.availableAnalysis.toMutableSet()
                    val analysisComputed = currentIteration.analysisComputed + first.updateAnalysis(availableAnalysis)

                    newIteration = IterationSearchNode(
                        passOrder = currentIteration.passOrder.toMutableList().apply { add(first) },
                        availableAnalysis = availableAnalysis,
                        closedPasses = currentIteration.closedPasses.toMutableSet()
                            .apply { add(first.nodeVisitor::class.java) },
                        passesLeft = currentIteration.passesLeft.filter { it.nodeVisitor::class.java != first.nodeVisitor::class.java },
                        analysisComputed = analysisComputed
                    )

                    break
                }

                open.addAll(openedNodes)
            }

            currentIteration = newIteration!!
        }

        return IteratedPassOrder(currentIteration.passOrder.map { it.nodeVisitor }.iterator())
    }
}