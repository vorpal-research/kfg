package org.jetbrains.research.kfg.util

import info.leadinglight.jdot.Edge
import info.leadinglight.jdot.Graph as JGraph
import info.leadinglight.jdot.Node
import info.leadinglight.jdot.enums.Color
import info.leadinglight.jdot.enums.Shape
import info.leadinglight.jdot.impl.Util
import java.io.File
import java.nio.file.Files
import java.util.*

class NoTopologicalSortingException(msg: String) : Exception(msg)

interface GraphNode<out T : Any> {
    val predecessors: Set<T>
    val successors: Set<T>
}

interface Graph<T : GraphNode<T>> {
    val entry: T
    val nodes: Set<T>
}

class GraphTraversal<T : GraphNode<T>>(val graph: Graph<T>) {
    private enum class Colour { WHITE, GREY, BLACK }

    fun <R> dfs(action: (T) -> R): List<R> {
        val node = graph.entry
        val search = mutableListOf<R>()
        val colours = mutableMapOf<T, Colour>()
        val stack = ArrayDeque<T>()
        stack.push(node)
        while (stack.isNotEmpty()) {
            val top = stack.pollLast()!!
            if (colours.getOrPut(top) { Colour.WHITE } == Colour.WHITE) {
                colours[top] = Colour.BLACK
                search.add(action(top))
                top.successors.filter { colours[it] != Colour.BLACK }.forEach { stack.push(it) }
            }
        }
        return search
    }

    fun dfs(): List<T> = dfs { it }

    fun <R> bfs(action: (T) -> R): List<R> {
        val node = graph.entry
        val search = mutableListOf<R>()
        val colours = mutableMapOf<T, Colour>()
        val stack = ArrayDeque<T>()
        stack.push(node)
        while (stack.isNotEmpty()) {
            val top = stack.poll()!!
            if (colours.getOrPut(top) { Colour.WHITE } == Colour.WHITE) {
                colours[top] = Colour.BLACK
                search.add(action(top))
                top.successors.filter { colours[it] != Colour.BLACK }.forEach { stack.push(it) }
            }
        }
        return search
    }

    fun bfs(): List<T> = bfs { it }

    fun <R> topologicalSort(action: (T) -> R): List<R> {
        val order = arrayListOf<R>()
        val colors = hashMapOf<T, Colour>()

        fun dfs(node: T) {
            if (colors.getOrPut(node) { Colour.WHITE } == Colour.BLACK) return
            if (colors.getValue(node) == Colour.GREY) {
                throw NoTopologicalSortingException("Could not perform topological sort")
            }
            colors[node] = Colour.GREY
            for (edge in node.successors)
                dfs(edge)
            colors[node] = Colour.BLACK
            order.add(action(node))
        }

        dfs(graph.entry)
        return order
    }

    fun topologicalSort(): List<T> = topologicalSort { it }
}

///////////////////////////////////////////////////////////////////////////////

@Deprecated("Use GraphTraversal instead")
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

fun <T : GraphNode<T>> Set<T>.asGraph(): Graph<T> = object : Graph<T> {
    override val entry: T
        get() = this@asGraph.first()
    override val nodes = this@asGraph
}

class LoopDetector<T : GraphNode<T>>(private val nodes: Set<T>) {
    fun search(): Map<T, List<T>> {
        val tree = DominatorTreeBuilder(nodes.asGraph()).build()
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
    JGraph.setDefaultCmd(dot)
    val graph = JGraph(name)
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