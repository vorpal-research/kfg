package org.jetbrains.research.kfg.util

enum class Color { WHITE, GREY, BLACK }

interface GraphNode {
    fun getEdges(): Set<GraphNode>
}

fun topologicalSort(node: GraphNode): Pair<List<GraphNode>, Set<GraphNode>> {
    val order = mutableListOf<GraphNode>()
    val cycled = mutableSetOf<GraphNode>()
    val colors = mutableMapOf<GraphNode, Color>()
    topologicalSort(node, order, cycled, colors)
    return Pair(order, cycled)
}

fun topologicalSort(node: GraphNode, order: MutableList<GraphNode>,
                    cycled: MutableSet<GraphNode>, colors: MutableMap<GraphNode, Color>) {
    if (colors.getOrPut(node, { Color.WHITE }) == Color.BLACK) return
    if (colors[node]!! == Color.GREY) {
        cycled.add(node)
        return
    }
    colors[node] = Color.GREY
    for (edge in node.getEdges())
        topologicalSort(edge, order, cycled, colors)
    colors[node] = Color.BLACK
    order.add(node)
}