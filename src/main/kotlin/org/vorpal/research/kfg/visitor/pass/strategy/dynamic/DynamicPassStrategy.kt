package org.vorpal.research.kfg.visitor.pass.strategy.dynamic

import org.vorpal.research.kfg.visitor.NodeVisitor
import org.vorpal.research.kfg.visitor.Pipeline
import org.vorpal.research.kfg.visitor.InternalVisitorRegistry
import org.vorpal.research.kfg.visitor.pass.AnalysisResult
import org.vorpal.research.kfg.visitor.pass.AnalysisVisitor
import org.vorpal.research.kfg.visitor.pass.strategy.IteratedPassOrder
import org.vorpal.research.kfg.visitor.pass.strategy.PassStrategy
import java.util.*

class DynamicPassStrategy : PassStrategy {
    override fun isParallelSupported() = false

    override fun createPassOrder(pipeline: Pipeline): IteratedPassOrder {
        val passes = pipeline.passes

        val open = passes.filter { pipeline.internalVisitorRegistry.getAnalysisDependencies(it::class.java).isEmpty() }
            .toCollection(LinkedList())
        var openSet = open.map { it::class.java }
        val closed = mutableSetOf<Class<out NodeVisitor>>()

        val passOrder = mutableListOf<NodeVisitor>()
        while (open.isNotEmpty()) {
            var bestCostOfInsertion0 = Int.MAX_VALUE
            var bestIndexToInsert0 = 0
            var bestNode = open[0]
            for (nodeToInsert in open) {
                // Calculate cost of adding at the end
                var bestCostOfInsertion = calculateCostAfterInsertion(passOrder, nodeToInsert, passOrder.size, pipeline.internalVisitorRegistry)
                var bestIndexToInsert = passOrder.size

                // Calculate cost of insertion in every other place
                for (i in 0 until passOrder.size) {
                    if (!isPossibleToInsert(passOrder, nodeToInsert, i, pipeline.internalVisitorRegistry)) {
                        continue
                    }

                    val costOfInsertion = calculateCostAfterInsertion(passOrder, nodeToInsert, i, pipeline.internalVisitorRegistry)
                    if (costOfInsertion < bestCostOfInsertion) {
                        bestCostOfInsertion = costOfInsertion
                        bestIndexToInsert = i
                    }
                }

                if (bestCostOfInsertion < bestCostOfInsertion0) {
                    bestCostOfInsertion0 = bestCostOfInsertion
                    bestIndexToInsert0 = bestIndexToInsert
                    bestNode = nodeToInsert
                }
            }

            val nodeToInsert = bestNode
            open.remove(bestNode)

            // Extend open list by looking for new available passes
            closed.add(nodeToInsert::class.java)
            val openedPasses = passes.filter { !closed.contains(it::class.java) }
                .filter { !openSet.contains(it::class.java) }
                .filter { closed.containsAll(pipeline.internalVisitorRegistry.getVisitorDependencies(it::class.java)) }
            open.addAll(openedPasses)
            openSet = open.map { it::class.java }

            passOrder.add(bestIndexToInsert0, nodeToInsert)
        }

        return IteratedPassOrder(passOrder.iterator())
    }

    private fun isPossibleToInsert(
        passOrder: List<NodeVisitor>,
        nodeToInsert: NodeVisitor,
        index: Int,
        visitorRegistry: InternalVisitorRegistry
    ): Boolean {
        val closed = mutableSetOf<Class<out NodeVisitor>>()
        for (i in 0 until index) {
            closed.add(passOrder[i]::class.java)
        }
        return closed.containsAll(visitorRegistry.getVisitorDependencies(nodeToInsert::class.java))
    }

    private fun calculateCostAfterInsertion(
        passOrder: List<NodeVisitor>,
        nodeToInsert: NodeVisitor,
        index: Int,
        visitorRegistry: InternalVisitorRegistry
    ): Int {
        // Returns new cost and updates cachedAnalysis set
        fun processPass(pass: NodeVisitor, cachedAnalysis: MutableSet<Class<out AnalysisVisitor<out AnalysisResult>>>, oldCost: Int): Int {
            val persistedAnalysis = visitorRegistry.getAnalysisPersisted(pass::class.java)
            val analysisToComputeCount = visitorRegistry.getAnalysisDependencies(pass::class.java)
                .count { !cachedAnalysis.contains(it) }

            val newCost = oldCost + 1 + analysisToComputeCount

            cachedAnalysis.removeAll { !persistedAnalysis.contains(it) }
            cachedAnalysis.addAll(visitorRegistry.getAnalysisDependencies(pass::class.java).filter { !persistedAnalysis.contains(it) })

            return newCost
        }

        var cost = 0
        val cachedAnalysis = mutableSetOf<Class<out AnalysisVisitor<out AnalysisResult>>>()

        for (i in 0 until index) {
            cost = processPass(passOrder[i], cachedAnalysis, cost)
        }
        cost = processPass(nodeToInsert, cachedAnalysis, cost)
        for (i in index until passOrder.size) {
            cost = processPass(passOrder[i], cachedAnalysis, cost)
        }

        return cost
    }
}