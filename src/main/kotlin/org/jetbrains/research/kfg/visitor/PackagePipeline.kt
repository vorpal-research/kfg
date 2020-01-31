package org.jetbrains.research.kfg.visitor

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.Node
import java.util.*

abstract class Pipeline(val cm: ClassManager, pipeline: List<NodeVisitor> = arrayListOf()) {
    protected val pipeline = pipeline.map { it.wrap() }.toMutableList()

    operator fun plus(visitor: NodeVisitor) = add(visitor)
    operator fun plusAssign(visitor: NodeVisitor) {
        add(visitor)
    }

    fun add(visitor: NodeVisitor) = pipeline.add(visitor.wrap())
    fun add(vararg visitors: NodeVisitor) {
        visitors.forEach { add(it) }
    }

    private fun NodeVisitor.wrap(): ClassVisitor = when (val visitor = this) {
        is ClassVisitor -> visitor
        is MethodVisitor -> object : ClassVisitor {
            override val cm get() = this@Pipeline.cm

            override fun cleanup() {
                visitor.cleanup()
            }

            override fun visitMethod(method: Method) {
                super.visitMethod(method)
                visitor.visit(method)
            }
        }
        else -> object : ClassVisitor {
            override val cm get() = this@Pipeline.cm

            override fun cleanup() {
                visitor.cleanup()
            }

            override fun visit(node: Node) {
                super.visit(node)
                visitor.visit(node)
            }
        }
    }

    operator fun NodeVisitor.unaryPlus() {
        pipeline.add(this.wrap())
    }

    abstract fun run()
}

class PackagePipeline(cm: ClassManager, val target: Package, pipeline: List<NodeVisitor> = arrayListOf()) : Pipeline(cm, pipeline) {
    override fun run() {
        val classes = cm.getByPackage(target)
        for (pass in pipeline) {
            for (`class` in classes) {
                pass.visit(`class`)
            }
        }
    }
}

class ClassPipeline(cm: ClassManager, target: Class, pipeline: List<NodeVisitor> = arrayListOf()) : Pipeline(cm, pipeline) {
    val targets = mutableSetOf<Class>()

    init {
        val classQueue = ArrayDeque<Class>(listOf(target))
        while (classQueue.isNotEmpty()) {
            val top = classQueue.pollFirst()
            targets += top
            classQueue.addAll(top.innerClasses.filterNot { it in targets })
        }
    }

    override fun run() {
        for (pass in pipeline) {
            for (`class` in targets) {
                pass.visit(`class`)
            }
        }
    }
}

fun buildPipeline(cm: ClassManager, target: Package, init: Pipeline.() -> Unit): Pipeline = PackagePipeline(cm, target).also {
    it.init()
}

fun buildPipeline(cm: ClassManager, target: Class, init: Pipeline.() -> Unit): Pipeline = ClassPipeline(cm, target).also {
    it.init()
}

fun executePipeline(cm: ClassManager, target: Package, init: Pipeline.() -> Unit) =
        buildPipeline(cm, target, init).run()

fun executePipeline(cm: ClassManager, target: Class, init: Pipeline.() -> Unit) =
        buildPipeline(cm, target, init).run()
