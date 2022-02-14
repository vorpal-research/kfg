package org.jetbrains.research.kfg.visitor.pass.strategy.astar

import org.jetbrains.research.kfg.visitor.NodeVisitor
import org.jetbrains.research.kfg.visitor.Pipeline
import org.jetbrains.research.kfg.visitor.VisitorRegistry
import org.jetbrains.research.kfg.visitor.pass.AnalysisResult
import org.jetbrains.research.kfg.visitor.pass.AnalysisVisitor
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

        val passes = pipeline.getPasses().map { NodeVisitorWrapper(it, pipeline.visitorRegistry) }

        val firstOpen = passes.filter { it.required.isEmpty() }
                .map { AStarNode(null, it.visitor, 0, 0, pipeline.visitorRegistry) }
        val open = PriorityQueue<AStarSearchNode> { e1, e2 -> e1.evaluation.compareTo(e2.evaluation) }
                .apply { add(AStarSearchNode(firstOpen, emptySet(), emptySet(), passes.size.toFloat() * 5, 0f, emptyList())) }

        var node = open.poll()
        var successNode: AStarSearchNode? = null
        var bestEval = Float.MAX_VALUE
        while (node != null) {
            for (n in node.open) {
                n.getMoves(passes, node.closed).forEach { move ->
                    val newClosed = move.getNewClosed(node.closed)
                    val filteredOpen = node.open.filter { !newClosed.contains(it.selectedPass::class.java) }
                    val list = filteredOpen.toMutableList()
                            .apply { add(move) }
                            .map { AStarNode(move, it.selectedPass, move.depth + 1, open.size, pipeline.visitorRegistry) }
                    val eval = move.getEvaluation(passes, node.closed, node.availableAnalysis, node.computedAnalysis)
                    val searchNode = AStarSearchNode(list,
                            newClosed,
                            move.getNewAvailable(node.availableAnalysis),
                            eval,
                            move.getAnalysisComputed(node.computedAnalysis, node.availableAnalysis),
                            node.prevPasses.toMutableList().apply { add(move.selectedPass) }
                    )
                    if (searchNode.closed.size == passes.size && searchNode.evaluation < bestEval) {
                        successNode = searchNode
                        bestEval = searchNode.evaluation
                    } else {
                        open.add(searchNode)
                    }
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
                         val selectedPass: NodeVisitor,
                         val depth: Int,
                         val openCount: Int,
                         val visitorRegistry: VisitorRegistry) {
    fun getMoves(passes: List<NodeVisitorWrapper>, closed: Set<Class<out NodeVisitor>>): List<AStarNode> {
        val newClosed = getNewClosed(closed)

        return passes.filter { !newClosed.contains(it.visitor::class.java) }
                .filter { closed.containsAll(it.required) }
                .map { AStarNode(this, it.visitor, depth, openCount, visitorRegistry) }
    }

    fun getPassesComputed(closed: Set<String>) = closed.size + 1
    fun getAnalysisComputed(analysisComputed: Float, availableAnalysis: Set<Class<out AnalysisVisitor<out AnalysisResult>>>) =
            analysisComputed + visitorRegistry.getAnalysisDependencies(selectedPass::class.java).filter { !availableAnalysis.contains(it) }.size
    fun getAnalysisLeft(passesLeft: List<NodeVisitorWrapper>) =
        passesLeft.sumOf { visitorRegistry.getAnalysisDependencies(it.visitor::class.java).size }

    fun getNewClosed(closed: Set<Class<out NodeVisitor>>) = closed.toMutableSet().apply { add(selectedPass::class.java) }
    fun getNewAvailable(availableAnalysis: Set<Class<out AnalysisVisitor<out AnalysisResult>>>) = availableAnalysis.toMutableSet().apply {
        addAll(visitorRegistry.getAnalysisDependencies(selectedPass::class.java))
        removeIf { !visitorRegistry.getAnalysisPersisted(selectedPass::class.java).contains(it) }
    }
    fun getPersistedLeft(passesLeft: List<NodeVisitorWrapper>) =
        passesLeft.sumOf { visitorRegistry.getAnalysisPersisted(it.visitor::class.java).size }
    fun getOpenFactor(passes: List<NodeVisitorWrapper>) = openCount / passes.size

    fun getEvaluation(
        passes: List<NodeVisitorWrapper>,
        closed: Set<Class<out NodeVisitor>>,
        availableAnalysis: Set<Class<out AnalysisVisitor<out AnalysisResult>>>,
        computedAnalysis: Float
    ): Float {
        val passesLeft = passes.filter { !closed.contains(it.visitor::class.java) }
        return passesLeft.size * 4.5f +
                getAnalysisComputed(computedAnalysis, availableAnalysis) +
                getAnalysisLeft(passesLeft) * 1.1f -
                (depth / 10) * 10000f -
                getOpenFactor(passes) * 0.05f -
                getNewAvailable(availableAnalysis).size
    }
}

internal class AStarSearchNode(
    val open: List<AStarNode>,
    val closed: Set<Class<out NodeVisitor>>,
    val availableAnalysis: Set<Class<out AnalysisVisitor<out AnalysisResult>>>,
    var evaluation: Float,
    val computedAnalysis: Float,
    val prevPasses: List<NodeVisitor>
)

internal class NodeVisitorWrapper(val visitor: NodeVisitor, val visitorRegistry: VisitorRegistry) {
    val required = visitorRegistry.getVisitorDependencies(visitor::class.java)
}



