package org.jetbrains.research.kfg.visitor.pass.strategy.dynamic

import org.jetbrains.research.kfg.visitor.NodeVisitor
import org.jetbrains.research.kfg.visitor.Pipeline
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

        val open = passes.filter { it.getRequiredPasses().isEmpty() }.toCollection(LinkedList())
        var openSet = open.map { it.getName() }
        val closed = mutableSetOf<String>()

        val passOrder = mutableListOf<NodeVisitor>()
        while (open.isNotEmpty()) {
            val nodeToInsert = open.poll()

            // Extend open list by looking for new available passes
            closed.add(nodeToInsert.getName())
            val openedPasses = passes.filter { !closed.contains(it.getName()) }
                .filter { !openSet.contains(it.getName()) }
                .filter { closed.containsAll(it.getRequiredPasses()) }
            open.addAll(openedPasses)
            openSet = open.map { it.getName() }


            // Calculate cost of adding at the end
            var bestCostOfInsertion = calculateCostAfterInsertion(passOrder, nodeToInsert, passOrder.size)
            var bestIndexToInsert = passOrder.size

            // Calculate cost of insertion in every other place
            for (i in 0 until passOrder.size) {
                if (!isPossibleToInsert(passOrder, nodeToInsert, i)) {
                    continue
                }

                val costOfInsertion = calculateCostAfterInsertion(passOrder, nodeToInsert, i)
                if (costOfInsertion < bestCostOfInsertion) {
                    bestCostOfInsertion = costOfInsertion
                    bestIndexToInsert = i
                }
            }

            passOrder.add(bestIndexToInsert, nodeToInsert)
        }

        return IteratedPassOrder(passOrder.iterator())
    }

    private fun isPossibleToInsert(passOrder: List<NodeVisitor>, nodeToInsert: NodeVisitor, index: Int): Boolean {
        val closed = mutableSetOf<String>()
        for (i in 0 until index) {
            closed.add(passOrder[i].getName())
        }
        return closed.containsAll(nodeToInsert.getRequiredPasses())
    }

    private fun calculateCostAfterInsertion(passOrder: List<NodeVisitor>, nodeToInsert: NodeVisitor, index: Int): Int {
        // Returns new cost and updates cachedAnalysis set
        fun processPass(pass: NodeVisitor, cachedAnalysis: MutableSet<String>, oldCost: Int): Int {
            val persistedAnalysis = pass.getPersistedAnalysisVisitors().toSet()
            val analysisToComputeCount = pass.getRequiredAnalysisVisitors()
                .count { !cachedAnalysis.contains(it) }

            val newCost = oldCost + 1 + analysisToComputeCount

            cachedAnalysis.removeAll { !persistedAnalysis.contains(it) }
            cachedAnalysis.addAll(pass.getRequiredAnalysisVisitors().filter { !persistedAnalysis.contains(it) })

            return newCost
        }

        var cost = 0
        val cachedAnalysis = mutableSetOf<String>()

        for (i in 0 until index) {
            cost = processPass(passOrder[i], cachedAnalysis, cost)
        }
        cost = processPass(nodeToInsert, cachedAnalysis, cost)
        for (i in index until passOrder.size) {
            cost = processPass(passOrder[i], cachedAnalysis, cost)
        }

        cost -= cachedAnalysis.size

        return cost
    }
}