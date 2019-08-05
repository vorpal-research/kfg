package org.jetbrains.research.kfg.util

import info.leadinglight.jdot.Edge
import info.leadinglight.jdot.Graph
import info.leadinglight.jdot.Node
import info.leadinglight.jdot.enums.Color
import info.leadinglight.jdot.enums.Shape
import info.leadinglight.jdot.impl.Util
import java.io.File
import java.nio.file.Files
import java.util.*

interface GraphNode<out T : Any> {
    val predecessors: Set<T>
    val successors: Set<T>
}

///////////////////////////////////////////////////////////////////////////////

class TopologicalSorter<T : GraphNode<T>>(private val nodes: Set<T>) {
    private val order = arrayListOf<T>()
    private val cycled = hashSetOf<T>()
    private val colors = hashMapOf<T, Color>()

    private enum class Color { WHITE, GREY, BLACK }

    private fun dfs(node: T) {
        if (colors.getOrPut(node) { Color.WHITE } == Color.BLACK) return
        if (colors.getValue(node) == Color.GREY) {
            cycled.add(node)
            return
        }
        colors[node] = Color.GREY
        for (edge in node.successors)
            dfs(edge)
        colors[node] = Color.BLACK
        order.add(node)
    }

    fun sort(node: T): Pair<List<T>, Set<T>> {
        require(node in nodes)
        dfs(node)
        return Pair(order, cycled)
    }
}

///////////////////////////////////////////////////////////////////////////////

class LoopDetector<T : GraphNode<T>>(private val nodes: Set<T>) {
    fun search(): Map<T, List<T>> {
        val tree = DominatorTreeBuilder(nodes).build()
        val backEdges = arrayListOf<Pair<T, T>>()

        for ((current, _) in tree) {
            for (succ in current.successors) {
                val succTreeNode = tree.getValue(succ)
                if (succTreeNode.dominates(current)) {
                    backEdges.add(succ to current)
                }
            }
        }

        val result = hashMapOf<T, MutableList<T>>()
        for ((header, end) in backEdges) {
            val body = arrayListOf(header)
            val stack = ArrayDeque<T>()
            stack.push(end)
            while (stack.isNotEmpty()) {
                val top = stack.pop()
                if (top !in body) {
                    body.add(top)
                    top.predecessors.forEach { stack.push(it) }
                }
            }
            result.getOrPut(header, ::arrayListOf).addAll(body)
        }
        return result
    }
}

///////////////////////////////////////////////////////////////////////////////

data class GraphView(
        val name: String,
        val label: String,
        val successors: MutableList<GraphView>
) {
    constructor(name: String, label: String) : this(name, label, mutableListOf())

    override fun hashCode(): Int {
        return simpleHash(name, label)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GraphView) return false

        if (name != other.name) return false
        if (label != other.label) return false
        return true
    }
}

fun viewCfg(name: String, nodes: List<GraphView>, dot: String, browser: String) {
    Graph.setDefaultCmd(dot)
    val graph = Graph(name)
    graph.addNodes(*nodes.map {
        Node(it.name).setShape(Shape.box).setLabel(it.label).setFontSize(12.0)
    }.toTypedArray())

    graph.setBgColor(Color.X11.transparent)

    for (node in nodes) {
        for (successor in node.successors) {
            graph.addEdge(Edge(node.name, successor.name))
        }
    }
    val file = graph.dot2file("svg")
    val newFile = "${file.removeSuffix("out")}svg"
    Files.move(File(file).toPath(), File(newFile).toPath())
    Util.sh(arrayOf(browser).plus("file://$newFile"))
}