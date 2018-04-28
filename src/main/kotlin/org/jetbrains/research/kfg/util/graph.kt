package org.jetbrains.research.kfg.util

import java.util.*

interface GraphNode<out T> {
    fun getPredSet(): Set<T>
    fun getSuccSet(): Set<T>
}

///////////////////////////////////////////////////////////////////////////////

class  TopologicalSorter<T: GraphNode<T>>(val nodes: Set<T>) {
    private val order = mutableListOf<T>()
    private val cycled = mutableSetOf<T>()
    private val colors = mutableMapOf<T, Color>()
    private enum class Color { WHITE, GREY, BLACK }

    private fun dfs(node: T) {
        if (colors.getOrPut(node, { Color.WHITE }) == Color.BLACK) return
        if (colors[node]!! == Color.GREY) {
            cycled.add(node)
            return
        }
        colors[node] = Color.GREY
        for (edge in node.getSuccSet())
            dfs(edge)
        colors[node] = Color.BLACK
        order.add(node)
    }

    fun sort(node: T): Pair<List<T>, Set<T>> {
        assert(node in nodes)
        dfs(node)
        return Pair(order, cycled)
    }
}

///////////////////////////////////////////////////////////////////////////////

class LoopDetector<T: GraphNode<T>>(val nodes: Set<T>) {
    fun search(): Map<T, List<T>> {
        val tree = DominatorTreeBuilder(nodes).build()
        val backEdges = mutableListOf<Pair<T, T>>()
        for ((current, _) in tree) {
            for (succ in current.getSuccSet()) {
                val succTreeNode = tree.getValue(succ)
                if (succTreeNode.dominates(current)) {
                    backEdges.add(succ to current)
                }
            }
        }
        val result = mutableMapOf<T, MutableList<T>>()
        for ((header, end) in backEdges) {
            val body = mutableListOf(header)
            val stack = Stack<T>()
            stack.push(end)
            while (stack.isNotEmpty()) {
                val top = stack.pop()
                if (top !in body) {
                    body.add(top)
                    top.getPredSet().forEach { stack.push(it) }
                }
            }
            result.getOrPut(header, { mutableListOf() }).addAll(body)
        }
        return result
    }
}