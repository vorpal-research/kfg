package org.jetbrains.research.kfg.visitor.pass.strategy.astar

import org.jetbrains.research.kfg.visitor.NodeVisitor
import org.jetbrains.research.kfg.visitor.Pipeline
import org.jetbrains.research.kfg.visitor.pass.strategy.IteratedPassOrder
import org.jetbrains.research.kfg.visitor.pass.strategy.PassOrder
import org.jetbrains.research.kfg.visitor.pass.strategy.PassStrategy
import java.util.*

class AStarPassStrategy : PassStrategy {
    override fun isParallelSupported(): Boolean = false

    override fun createPassOrder(pipeline: Pipeline, parallel: Boolean): PassOrder {
        if (parallel) {
            throw NotImplementedError("Parallel execution is not supported for this pass order")
        }

        val passes = pipeline.getPasses().map { NodeVisitorWrapper(it) }

        val firstOpen = passes.filter { it.required.isEmpty() }
                .map { AStarNode(null, it.visitor) }
        val open = PriorityQueue<AStarSearchNode> { e1, e2 -> e1.evaluation.compareTo(e2.evaluation) }
                .apply { add(AStarSearchNode(firstOpen, emptySet(), emptySet(), passes.size.toFloat() * 5, 0f, emptyList())) }

        var node = open.poll()
        var successNode: AStarSearchNode? = null
        while (node != null) {
            for (n in node.open) {
                n.getMoves(passes, node.closed).forEach { move ->
                    val newClosed = move.getNewClosed(node.closed)
                    val list = node.open.filter { !newClosed.contains(it.selectedPass.getName()) }
                            .toMutableList()
                            .apply { add(move) }
                            .map { AStarNode(move, it.selectedPass) }
                    val eval = move.getEvaluation(passes, node.closed, node.availableAnalysis, node.computedAnalysis)
                    val searchNode = AStarSearchNode(list,
                            newClosed,
                            move.getNewAvailable(node.availableAnalysis),
                            eval,
                            move.getAnalysisComputed(node.computedAnalysis, node.availableAnalysis),
                            node.prevPasses.toMutableList().apply { add(move.selectedPass) }
                    )
                    if (searchNode.closed.size == passes.size) {
                        successNode = searchNode
                    }
                    open.add(searchNode)
                }
            }

            if (successNode != null) {
                break
            }

            node = open.poll()
        }

        return IteratedPassOrder(successNode!!.prevPasses.iterator())
    }
}

internal class AStarNode(val parent: AStarNode?,
                         val selectedPass: NodeVisitor) {
    fun getMoves(passes: List<NodeVisitorWrapper>, closed: Set<String>): List<AStarNode> {
        val newClosed = getNewClosed(closed)

        return passes.filter { !newClosed.contains(it.visitor.getName()) }
                .filter { closed.containsAll(it.required) }
                .map { AStarNode(this, it.visitor) }
    }

    fun getPassesComputed(closed: Set<String>) = closed.size + 1
    fun getAnalysisComputed(analysisComputed: Float, availableAnalysis: Set<String>) =
            analysisComputed + selectedPass.getRequiredAnalysisVisitors().filter { !availableAnalysis.contains(it) }.size
    fun getPassesLeft(passes: List<NodeVisitorWrapper>, closed: Set<String>) = passes.size - closed.size - 1
    fun getNewClosed(closed: Set<String>) = closed.toMutableSet().apply { add(selectedPass.getName()) }
    fun getNewAvailable(availableAnalysis: Set<String>) = availableAnalysis.toMutableSet().apply {
        addAll(selectedPass.getRequiredAnalysisVisitors())
        removeIf { !selectedPass.getPersistedAnalysisVisitors().contains(it) }
    }
    fun getEvaluation(passes: List<NodeVisitorWrapper>, closed: Set<String>, availableAnalysis: Set<String>, computedAnalysis: Float) =
            getPassesLeft(passes, closed) * 50f + computedAnalysis
}

internal class AStarSearchNode(val open: List<AStarNode>, val closed: Set<String>, val availableAnalysis: Set<String>, var evaluation: Float, val computedAnalysis: Float, val prevPasses: List<NodeVisitor>)

internal class NodeVisitorWrapper(val visitor: NodeVisitor) {
    val required = visitor.getRequiredPasses().toSet()
}



