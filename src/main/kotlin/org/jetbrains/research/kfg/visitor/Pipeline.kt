package org.jetbrains.research.kfg.visitor

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.Node

class Pipeline(val cm: ClassManager, private val target: Package, pipeline: List<NodeVisitor> = arrayListOf()) {
    private val pipeline = pipeline.map { it.wrap() }.toMutableList()

    operator fun plus(visitor: NodeVisitor) = add(visitor)
    operator fun plusAssign(visitor: NodeVisitor) {
        add(visitor)
    }

    fun add(visitor: NodeVisitor) = pipeline.add(visitor.wrap())
    fun add(vararg visitors: NodeVisitor) {
        visitors.forEach { add(it) }
    }

    fun run() {
        val classes = cm.getByPackage(target)
        for (pass in pipeline) {
            for (`class` in classes) {
                pass.visit(`class`)
            }
        }
    }

    private fun NodeVisitor.wrap(): ClassVisitor = when (this) {
        is ClassVisitor -> this
        is MethodVisitor -> object : ClassVisitor {
            override val cm get() = this@Pipeline.cm

            override fun cleanup() {
                this.cleanup()
            }

            override fun visitMethod(method: Method) {
                super.visitMethod(method)
                this.visit(method)
            }
        }
        else -> object : ClassVisitor {
            override val cm get() = this@Pipeline.cm

            override fun cleanup() {
                this.cleanup()
            }

            override fun visit(node: Node) {
                super.visit(node)
                this.visit(node)
            }
        }
    }

    operator fun NodeVisitor.unaryPlus() {
        pipeline.add(this.wrap())
    }
}

fun buildPipeline(cm: ClassManager, target: Package, init: Pipeline.() -> Unit): Pipeline {
    val ppl = Pipeline(cm, target)
    ppl.init()
    return ppl
}

fun executePipeline(cm: ClassManager, target: Package, init: Pipeline.() -> Unit) {
    buildPipeline(cm, target, init).run()
}
