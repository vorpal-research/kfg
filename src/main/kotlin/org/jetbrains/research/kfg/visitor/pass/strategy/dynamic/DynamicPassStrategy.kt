package org.jetbrains.research.kfg.visitor.pass.strategy.dynamic

import org.jetbrains.research.kfg.visitor.NodeVisitor
import org.jetbrains.research.kfg.visitor.Pipeline
import org.jetbrains.research.kfg.visitor.VisitorRegistry
import org.jetbrains.research.kfg.visitor.pass.AnalysisResult
import org.jetbrains.research.kfg.visitor.pass.AnalysisVisitor
import org.jetbrains.research.kfg.visitor.pass.strategy.IteratedPassOrder
import org.jetbrains.research.kfg.visitor.pass.strategy.PassStrategy
import java.util.*

class DynamicPassStrategy : PassStrategy {
    override fun isParallelSupported() = false

    override fun createPassOrder(pipeline: Pipeline, parallel: Boolean): IteratedPassOrder {
        if (parallel) {
            throw NotImplementedError("Parallel execution is not supported for this pass order")
        }

        val passes = pipeline.getPasses()

        val open = passes.filter { pipeline.visitorRegistry.getAnalysisDependencies(it::class.java).isEmpty() }
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
                var bestCostOfInsertion = calculateCostAfterInsertion(passOrder, nodeToInsert, passOrder.size, pipeline.visitorRegistry)
                var bestIndexToInsert = passOrder.size

                // Calculate cost of insertion in every other place
                for (i in 0 until passOrder.size) {
                    if (!isPossibleToInsert(passOrder, nodeToInsert, i, pipeline.visitorRegistry)) {
                        continue
                    }

                    val costOfInsertion = calculateCostAfterInsertion(passOrder, nodeToInsert, i, pipeline.visitorRegistry)
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
                .filter { closed.containsAll(pipeline.visitorRegistry.getVisitorDependencies(it::class.java)) }
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
        visitorRegistry: VisitorRegistry
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
        visitorRegistry: VisitorRegistry
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

        //cost -= cachedAnalysis.size

        return cost
    }
}