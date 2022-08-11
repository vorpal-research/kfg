package org.vorpal.research.kfg.visitor.pass.strategy.astar

import kotlinx.collections.immutable.*
import org.vorpal.research.kfg.visitor.NodeVisitor
import org.vorpal.research.kfg.visitor.Pipeline
import org.vorpal.research.kfg.visitor.InternalVisitorRegistry
import org.vorpal.research.kfg.visitor.pass.AnalysisResult
import org.vorpal.research.kfg.visitor.pass.AnalysisVisitor
import org.vorpal.research.kfg.visitor.pass.strategy.IteratedPassOrder
import org.vorpal.research.kfg.visitor.pass.strategy.PassOrder
import org.vorpal.research.kfg.visitor.pass.strategy.PassStrategy
import java.util.*

class AStarPassStrategy : PassStrategy {
    override fun isParallelSupported(): Boolean = false

    override fun createPassOrder(pipeline: Pipeline): PassOrder {
        val passes = pipeline.passes.map { NodeVisitorWrapper(it, pipeline.internalVisitorRegistry) }
            .toPersistentList()

        val firstOpen = passes.filter { it.required.isEmpty() }
                .map { AStarNode(null, it.visitor, 0, 0, pipeline.internalVisitorRegistry) }
                .toPersistentList()
        val open = PriorityQueue<AStarSearchNode> { e1, e2 -> e1.evaluation.compareTo(e2.evaluation) }
                .apply { add(AStarSearchNode(firstOpen, persistentSetOf(), persistentSetOf(), passes.size.toFloat() * 5, 0f, persistentListOf())) }

        var node = open.poll()
        var successNode: AStarSearchNode? = null
        var bestEval = Float.MAX_VALUE
        while (node != null) {
            for (n in node.open) {
                n.getMoves(passes, node.closed).forEach { move ->
                    val newClosed = move.getNewClosed(node.closed)
                    val filteredOpen = node.open.filter { !newClosed.contains(it.selectedPass::class.java) }
                    val list = (filteredOpen + move)
                            .map { AStarNode(move, it.selectedPass, move.depth + 1, open.size, pipeline.internalVisitorRegistry) }
                    val eval = move.getEvaluation(passes, node.closed, node.availableAnalysis, node.computedAnalysis)
                    val searchNode = AStarSearchNode(list.toPersistentList(),
                            newClosed,
                            move.getNewAvailable(node.availableAnalysis).toPersistentSet(),
                            eval,
                            move.getAnalysisComputed(node.computedAnalysis, node.availableAnalysis),
                            node.prevPasses + move.selectedPass
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
                         val visitorRegistry: InternalVisitorRegistry) {
    fun getMoves(passes: PersistentList<NodeVisitorWrapper>, closed: PersistentSet<Class<out NodeVisitor>>): PersistentList<AStarNode> {
        val newClosed = getNewClosed(closed)

        return passes.toList().filter { !newClosed.contains(it.visitor::class.java) }
                .filter { closed.containsAll(it.required) }
                .map { AStarNode(this, it.visitor, depth, openCount, visitorRegistry) }
                .toPersistentList()
    }

    fun getAnalysisComputed(analysisComputed: Float, availableAnalysis: Set<Class<out AnalysisVisitor<out AnalysisResult>>>) =
            analysisComputed + visitorRegistry.getAnalysisDependencies(selectedPass::class.java).filter { !availableAnalysis.contains(it) }.size

    fun getAnalysisLeft(passesLeft: List<NodeVisitorWrapper>) =
        passesLeft.sumOf { visitorRegistry.getAnalysisDependencies(it.visitor::class.java).size }

    fun getNewClosed(closed: PersistentSet<Class<out NodeVisitor>>) = closed + selectedPass::class.java

    fun getNewAvailable(availableAnalysis: Set<Class<out AnalysisVisitor<out AnalysisResult>>>) = availableAnalysis.toMutableSet().apply {
        addAll(visitorRegistry.getAnalysisDependencies(selectedPass::class.java))
        removeIf { !visitorRegistry.getAnalysisPersisted(selectedPass::class.java).contains(it) }
    }

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
    val open: PersistentList<AStarNode>,
    val closed: PersistentSet<Class<out NodeVisitor>>,
    val availableAnalysis: PersistentSet<Class<out AnalysisVisitor<out AnalysisResult>>>,
    var evaluation: Float,
    val computedAnalysis: Float,
    val prevPasses: PersistentList<NodeVisitor>
)

internal class NodeVisitorWrapper(val visitor: NodeVisitor, val visitorRegistry: InternalVisitorRegistry) {
    val required = visitorRegistry.getVisitorDependencies(visitor::class.java)
}



