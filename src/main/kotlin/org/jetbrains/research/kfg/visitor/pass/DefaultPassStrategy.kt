package org.jetbrains.research.kfg.visitor.pass

import org.jetbrains.research.kfg.visitor.NodeVisitor
import org.jetbrains.research.kfg.visitor.Pipeline
import java.util.*
import kotlin.math.max

class DefaultPassStrategy : PassStrategy {
    override fun isParallelSupported() = false

    override fun createPassOrder(pipeline: Pipeline, parallel: Boolean): PassOrder {
        if (parallel) {
            throw NotImplementedError("Parallel execution is not supported for this pass order")
        }

        val graphNodes = IdentityHashMap<Class<NodeVisitor>, GraphNode>()
        val passes = pipeline.getPasses()

        passes.forEach {
            graphNodes[it.getOriginalClass()] = GraphNode(it)
        }

        passes.forEach {
            val graphNode = graphNodes[it.getOriginalClass()]
            it.getRequiredPasses().forEach { required ->
                graphNode!!.parents.add(graphNodes[required]!!)
            }
        }

        val passOrder = DefaultPassOrder()
        passes.forEach {
            passOrder.append(graphNodes[it.getOriginalClass()]!!)
        }

        return passOrder.apply { build() }
    }
}

class DefaultPassOrder : PassOrder {
    private val layers = mutableListOf<MutableList<NodeVisitor>>()
    private lateinit var layersIterator: MutableIterator<MutableList<NodeVisitor>>
    private var singleLayerIterator: Iterator<NodeVisitor>? = null

    internal fun append(node: GraphNode) {
        val depth = node.depth

        while (layers.size <= depth) {
            layers.add(mutableListOf())
        }

        layers[depth].add(node.visitor)
    }

    internal fun build() {
        layersIterator = layers.iterator()
    }

    override fun hasNext(): Boolean {
        return singleLayerIterator != null && singleLayerIterator!!.hasNext() || layersIterator.hasNext()
    }

    override fun getNext(): NodeVisitor {
        if (singleLayerIterator != null && singleLayerIterator!!.hasNext()) {
            return singleLayerIterator!!.next()
        }

        singleLayerIterator = layersIterator.next().iterator()
        return getNext()
    }

    override fun isParallelSupported() = false
    override fun getNextParallel(): NodeVisitor = throw NotImplementedError("Parallel execution is not supported for this pass order")
    override fun completedParallel(pass: NodeVisitor) = throw NotImplementedError("Parallel execution is not supported for this pass order")
}

internal class GraphNode(val visitor: NodeVisitor) {
    val parents = mutableListOf<GraphNode>()
    private var _depth = -1
    val depth get(): Int {
        if (_depth == -1) {
            computeDepth()
        }
        return _depth
    }

    private fun computeDepth() {
        var maxDepth = 0
        parents.forEach {
            maxDepth = max(maxDepth, it.depth + 1)
        }
        _depth = maxDepth
    }
}