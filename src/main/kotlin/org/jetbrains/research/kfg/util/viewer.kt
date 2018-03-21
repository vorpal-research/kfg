package org.jetbrains.research.kfg.util

import info.leadinglight.jdot.Edge
import info.leadinglight.jdot.Graph
import info.leadinglight.jdot.Node
import info.leadinglight.jdot.enums.Shape
import org.jetbrains.research.kfg.ir.Method
import kotlin.concurrent.thread

fun viewCfg(method: Method) {
    Graph.DEFAULT_CMD = "/usr/bin/dot"
    Graph.DEFAULT_BROWSER_CMD = arrayOf("/usr/bin/chromium")
    val graph = Graph()
    method.basicBlocks.forEach {
        val node = Node(it.name).setShape(Shape.box).setLabel(it.name)
        graph.addNode(node)
    }
    method.basicBlocks.forEach {
        for (succ in it.successors) {
            graph.addEdge(Edge(it.name, succ.name))
        }
    }
    thread {
        graph.viewSvg()
    }
}