package org.vorpal.research.kfg.visitor.pass.strategy.topologic

import org.vorpal.research.kfg.visitor.NodeVisitor
import org.vorpal.research.kfg.visitor.Pipeline
import org.vorpal.research.kfg.visitor.pass.strategy.PassOrder
import org.vorpal.research.kfg.visitor.pass.strategy.PassStrategy
import kotlin.math.max

class DefaultPassStrategy : PassStrategy {
    override fun isParallelSupported() = false

    override fun createPassOrder(pipeline: Pipeline): PassOrder {
        val graphNodes = mutableMapOf<Class<out NodeVisitor>, GraphNode>()
        val passes = pipeline.passes

        passes.forEach {
            graphNodes[it::class.java] = GraphNode(it)
        }

        passes.forEach {
            val graphNode = graphNodes[it::class.java]
            pipeline.internalVisitorRegistry.getVisitorDependencies(it::class.java).forEach { required ->
                graphNode!!.parents.add(graphNodes[required]!!)
            }
        }

        val passOrder = DefaultPassOrder()
        passes.forEach {
            passOrder.append(graphNodes[it::class.java]!!)
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

    override fun next(): NodeVisitor {
        if (singleLayerIterator != null && singleLayerIterator!!.hasNext()) {
            return singleLayerIterator!!.next()
        }

        singleLayerIterator = layersIterator.next().iterator()
        return next()
    }
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