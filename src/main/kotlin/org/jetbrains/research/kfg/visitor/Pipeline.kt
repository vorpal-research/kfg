package org.jetbrains.research.kfg.visitor

import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.Node

class Pipeline(val target: Package, pipeline: Set<NodeVisitor> = hashSetOf()) {
    private val pipeline = pipeline.map { wrap(it) }.toHashSet()

    operator fun plus(visitor: NodeVisitor) = add(visitor)
    operator fun plusAssign(visitor: NodeVisitor) {
        add(visitor)
    }

    fun add(visitor: NodeVisitor) = pipeline.add(wrap(visitor))
    fun add(vararg visitors: NodeVisitor) {
        visitors.forEach { add(it) }
    }

    private fun wrap(visitor: NodeVisitor): ClassVisitor = when (visitor) {
        is ClassVisitor -> visitor
        is MethodVisitor -> object : ClassVisitor {
            override fun cleanup() {
                visitor.cleanup()
            }

            override fun visitMethod(method: Method) {
                super.visitMethod(method)
                visitor.visit(method)
            }
        }
        else -> object : ClassVisitor {
            override fun cleanup() {
                visitor.cleanup()
            }

            override fun visit(node: Node) {
                super.visit(node)
                visitor.visit(node)
            }
        }
    }

    fun run() {
        val classes = CM.getByPackage(target)
        for (pass in pipeline) {
            classes.forEach { pass.visit(it) }
        }
    }
}