package org.jetbrains.research.kfg.visitor

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.Node

class Pipeline(val cm: ClassManager, val target: Package, pipeline: List<NodeVisitor> = arrayListOf()) {
    private val pipeline = pipeline.asSequence().map { wrap(it) }.toMutableList()

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
            override val cm get() = visitor.cm

            override fun cleanup() {
                visitor.cleanup()
            }

            override fun visitMethod(method: Method) {
                super.visitMethod(method)
                visitor.visit(method)
            }
        }
        else -> object : ClassVisitor {
            override val cm get() = visitor.cm

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
        val classes = cm.getByPackage(target)
        for (pass in pipeline) {
            for (`class` in classes) {
                pass.visit(`class`)
            }
        }
    }
}