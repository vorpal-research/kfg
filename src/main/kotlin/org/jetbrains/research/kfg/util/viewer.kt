package org.jetbrains.research.kfg.util

import info.leadinglight.jdot.Edge
import info.leadinglight.jdot.Graph
import info.leadinglight.jdot.Node
import info.leadinglight.jdot.enums.Shape
import info.leadinglight.jdot.enums.Style
import org.jetbrains.research.kfg.ir.CatchBlock
import org.jetbrains.research.kfg.ir.Method

fun viewCfg(method: Method, viewCatchBlocks: Boolean = false) {
    Graph.DEFAULT_CMD = "/usr/bin/dot"
    Graph.DEFAULT_BROWSER_CMD = arrayOf("/usr/bin/chromium")
    val graph = Graph(method.name)
    val name = Node(method.name).setShape(Shape.oval).setLabel(method.toString()).setFontName("ttf-fira-mono").setFontSize(12.0)
    graph.addNode(name)
    method.basicBlocks.forEach {
        val label = StringBuilder()
        label.append("${it.name}:\\l")
        it.instructions.forEach { label.append("    ${it.print().replace("\"", "\\\"")}\\l") }
        val node = Node(it.name.toString()).setShape(Shape.box).setLabel(label.toString()).setFontName("ttf-fira-mono").setFontSize(12.0)
        graph.addNode(node)
    }
    if (!method.isAbstract()) graph.addEdge(Edge(method.name, method.getEntry().name.toString()))
    method.basicBlocks.forEach {
        for (succ in it.successors) {
            graph.addEdge(Edge(it.name.toString(), succ.name.toString()))
        }
    }
    if (viewCatchBlocks) {
        method.catchEntries.forEach {
            it as CatchBlock
            for (thrower in it.getAllThrowers()) {
                val edge = Edge(thrower.name.toString(), it.name.toString()).setStyle(Style.Edge.dotted)
                graph.addEdge(edge)
            }
        }
    }
    graph.viewSvg()
}