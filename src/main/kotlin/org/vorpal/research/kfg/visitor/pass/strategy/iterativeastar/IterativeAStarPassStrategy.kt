package org.vorpal.research.kfg.visitor.pass.strategy.iterativeastar

import kotlinx.collections.immutable.*
import org.vorpal.research.kfg.visitor.Pipeline
import org.vorpal.research.kfg.visitor.pass.strategy.IteratedPassOrder
import org.vorpal.research.kfg.visitor.pass.strategy.PassOrder
import org.vorpal.research.kfg.visitor.pass.strategy.PassStrategy
import java.util.*

// Use iterative way - limit the depth by 10 (for example) and try our best at this depth
private const val ITERATIVE_DEPTH = 15

class IterativeAStarPassStrategy : PassStrategy {
    override fun isParallelSupported() = false

    override fun createPassOrder(pipeline: Pipeline): PassOrder {
        val allPasses = pipeline.passes.map { VisitorWrapper(it, pipeline.internalVisitorRegistry) }

        var currentIteration = IterationSearchNode(
            passOrder = persistentListOf(),
            availableAnalysis = emptySet(),
            closedPasses = persistentSetOf(),
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
                        passOrder = persistentListOf(it),
                        availableAnalysis = availableAnalysis,
                        closedPasses = persistentSetOf(it.nodeVisitor::class.java),
                        analysisComputed = analysisComputed,
                        openNodesCount = 0,
                        pipeline.internalVisitorRegistry
                    )
                }

            val open = PriorityQueue<SearchNode> { first, second -> first.evaluation.compareTo(second.evaluation) }
            open.addAll(openSearchNodes)

            var newIteration: IterationSearchNode? = null
            while (newIteration == null) {
                val currentNode = open.poll()

                val openedNodes = currentNode.openNodes(currentIteration, open, pipeline.internalVisitorRegistry)

                val suitableForNextIteration = currentNode.getSuitableNode(openedNodes, ITERATIVE_DEPTH)

                if (suitableForNextIteration != null) {
                    open.clear()

                    newIteration = IterationSearchNode(
                        passOrder = currentIteration.passOrder.addAll(suitableForNextIteration.passOrder),
                        availableAnalysis = suitableForNextIteration.availableAnalysis,
                        closedPasses = currentIteration.closedPasses.addAll(suitableForNextIteration.closedPasses),
                        passesLeft = currentIteration.passesLeft.filter {
                            !suitableForNextIteration.closedPasses.contains(
                                it.nodeVisitor::class.java
                            )
                        },
                        analysisComputed = suitableForNextIteration.analysisComputed
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