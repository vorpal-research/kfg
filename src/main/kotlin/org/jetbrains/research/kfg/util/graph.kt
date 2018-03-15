package org.jetbrains.research.kfg.util

enum class Color { WHITE, GREY, BLACK }

class Node<T: Any>(val value: T) {
    val successors = mutableSetOf<Node<T>>()

    override fun toString() = value.toString()
    override fun hashCode() = value.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Node<*>
        return this.value == other.value
    }
}

fun <T: Any> topologicalSort(node: Node<T>): Pair<List<Node<T>>, Set<Node<T>>> {
    val order = mutableListOf<Node<T>>()
    val cycled = mutableSetOf<Node<T>>()
    val colors = mutableMapOf<Node<T>, Color>()
    topologicalSort(node, order, cycled, colors)
    return Pair(order, cycled)
}

fun <T: Any> topologicalSort(node: Node<T>, order: MutableList<Node<T>>, cycled: MutableSet<Node<T>>, colors: MutableMap<Node<T>, Color>) {
    if (colors.getOrPut(node, { Color.WHITE }) == Color.BLACK) return
    if (colors[node]!! == Color.GREY) {
        cycled.add(node)
        return
    }
    colors[node] = Color.GREY
    for (edge in node.successors)
        topologicalSort(edge, order, cycled, colors)
    colors[node] = Color.BLACK
    order.add(node)
}