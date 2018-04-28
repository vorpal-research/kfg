package org.jetbrains.research.kfg.util

import java.util.*
import kotlin.math.min

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

class DominatorTreeNode<T: GraphNode<T>>(val value: T): TreeNode {
    internal var idom: DominatorTreeNode<T>? = null
    internal val dominates = mutableSetOf<DominatorTreeNode<T>>()

    override fun getChilds(): Set<DominatorTreeNode<T>> = dominates
    override fun getParent() = idom

    fun dominates(node: T): Boolean {
        for (it in dominates) {
            if (it.value == node) return true
        }
        for (it in dominates) {
            if (it.dominates(node)) return true
        }
        return false
    }
}

class DominatorTreeBuilder<T: GraphNode<T>>(val nodes: Set<T>) {
    val tree = mutableMapOf<T, DominatorTreeNode<T>>()

    private var nodeCounter: Int = 0
    private val dfsTree = mutableMapOf<T, Int>()
    private val reverseMapping = arrayListOf<T?>()
    private val reverseGraph = arrayListOf<ArrayList<Int>>()
    private val parents = arrayListOf<Int>()
    private val labels = arrayListOf<Int>()
    private val sdom = arrayListOf<Int>()
    private val dom = arrayListOf<Int>()
    private val dsu = arrayListOf<Int>()
    private val bucket = arrayListOf<MutableSet<Int>>()

    init {
        for (i in nodes) {
            parents.add(-1)
            labels.add(-1)
            sdom.add(-1)
            dom.add(-1)
            dsu.add(-1)
            reverseMapping.add(null)
            dfsTree[i] = -1
            bucket.add(mutableSetOf())
            reverseGraph.add(arrayListOf())
        }
        tree.putAll(nodes.map { it to DominatorTreeNode(it) })
    }

    private fun union(u: Int, v: Int) {
        dsu[v] = u
    }

    private fun find(u: Int, x: Int = 0): Int {
        if (u < 0) return u
        if (u == dsu[u]) return if (x != 0) -1 else u
        val v = find(dsu[u], x + 1)
        if (v < 0) return u
        if (sdom[labels[dsu[u]]] < sdom[labels[u]]) labels[u] = labels[dsu[u]]
        dsu[u] = v
        return if (x != 0) v else labels[u]
    }

    private fun dfs(node: T) {
        dfsTree[node] = nodeCounter
        reverseMapping[nodeCounter] = node
        labels[nodeCounter] = nodeCounter
        sdom[nodeCounter] = nodeCounter
        dsu[nodeCounter] = nodeCounter
        nodeCounter++
        for (it in node.getSuccSet()) {
            if (dfsTree.getValue(it) == -1) {
                dfs(it)
                parents[dfsTree.getValue(it)] = dfsTree.getValue(node)
            }
            reverseGraph[dfsTree.getValue(it)].add(dfsTree.getValue(node))
        }
    }

    fun build(): Map<T, DominatorTreeNode<T>> {
        for (it in nodes) if (dfsTree[it] == -1) dfs(it)
        val n = dfsTree.size
        for (i in n - 1 downTo 0) {
            for (j in reverseGraph[i]) {
                sdom[i] = min(sdom[i], sdom[find(j)])
            }
            if (i > 0) bucket[sdom[i]].add(i)
            for (j in bucket[i]) {
                val v = find(j)
                if (sdom[v] == sdom[j]) dom[j] = sdom[j]
                else dom[j] = v
            }
            if (i > 0) union(parents[i], i)
        }
        for (i in 1 until n) {
            if (dom[i] != sdom[i]) dom[i] = dom[dom[i]]
        }
        for ((it, idom) in dom.withIndex()) {
            val current = reverseMapping[it]!!
            if (idom != -1) {
                val dominator = reverseMapping[idom]!!
                tree.getValue(dominator).dominates.add(tree.getValue(current))
                tree.getValue(current).idom = tree.getValue(dominator)
            }
        }
        for (it in tree) {
            if (it.key == it.value.idom?.value) it.value.idom = null
        }
        return tree
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