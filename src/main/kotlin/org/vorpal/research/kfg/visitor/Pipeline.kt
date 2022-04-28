package org.vorpal.research.kfg.visitor

import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.Node
import java.util.*

abstract class Pipeline(val cm: org.vorpal.research.kfg.ClassManager, pipeline: List<NodeVisitor> = arrayListOf()) {
    protected open val pipeline = pipeline.map { it.wrap() }.toMutableList()

    operator fun plus(visitor: NodeVisitor) = add(visitor)
    operator fun plusAssign(visitor: NodeVisitor) {
        add(visitor)
    }

    open fun add(visitor: NodeVisitor) = pipeline.add(visitor.wrap())
    fun add(vararg visitors: NodeVisitor) {
        visitors.forEach { add(it) }
    }

    protected fun NodeVisitor.wrap(): ClassVisitor = when (val visitor = this) {
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
        add(this)
    }

    abstract fun run()
}

class PackagePipeline(
    cm: org.vorpal.research.kfg.ClassManager,
    val target: org.vorpal.research.kfg.Package,
    pipeline: List<NodeVisitor> = arrayListOf()
) : Pipeline(cm, pipeline) {
    override fun run() {
        val classes = cm.getByPackage(target)
        for (pass in pipeline) {
            for (`class` in classes) {
                pass.visit(`class`)
            }
        }
    }
}

class MultiplePackagePipeline(
    cm: org.vorpal.research.kfg.ClassManager,
    val targets: List<org.vorpal.research.kfg.Package>,
    pipeline: List<NodeVisitor> = arrayListOf()
) : Pipeline(cm, pipeline) {
    override fun run() {
        val classes = targets.flatMap { cm.getByPackage(it) }
        for (pass in pipeline) {
            for (`class` in classes) {
                pass.visit(`class`)
            }
        }
    }
}

class ClassPipeline(
    cm: org.vorpal.research.kfg.ClassManager,
    target: Class,
    pipeline: List<NodeVisitor> = arrayListOf()
) : Pipeline(cm, pipeline) {
    private val targets = mutableSetOf<Class>()

    init {
        val classQueue = ArrayDeque<Class>(listOf(target))
        while (classQueue.isNotEmpty()) {
            val top = classQueue.pollFirst()
            targets += top
            classQueue.addAll(top.innerClasses.keys.filterNot { it in targets })
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

class MethodPipeline(
    cm: org.vorpal.research.kfg.ClassManager,
    val targets: Collection<Method>,
    pipeline: List<NodeVisitor> = arrayListOf()
) : Pipeline(cm, pipeline) {
    private val classTargets = targets.map { it.klass }.toMutableSet()
    override val pipeline = pipeline.map { it.methodWrap() }.toMutableList()

    protected fun NodeVisitor.methodWrap(): ClassVisitor = when (val visitor = this) {
        is ClassVisitor -> object : ClassVisitor {
            override val cm get() = this@MethodPipeline.cm

            override fun cleanup() {
                visitor.cleanup()
            }

            override fun visit(klass: Class) {
                super.visit(klass)
                visitor.visit(klass)
            }

            override fun visitMethod(method: Method) {
                if (method in targets) {
                    super.visitMethod(method)
                    visitor.visitMethod(method)
                }
            }
        }
        is MethodVisitor -> object : ClassVisitor {
            override val cm get() = this@MethodPipeline.cm

            override fun cleanup() {
                visitor.cleanup()
            }

            override fun visitMethod(method: Method) {
                if (method in targets) {
                    super.visitMethod(method)
                    visitor.visit(method)
                }
            }
        }
        else -> this.wrap()
    }

    override fun add(visitor: NodeVisitor) = pipeline.add(visitor.methodWrap())

    override fun run() {
        for (pass in pipeline) {
            for (`class` in classTargets) {
                pass.visit(`class`)
            }
        }
    }
}

fun buildPipeline(cm: org.vorpal.research.kfg.ClassManager, target: org.vorpal.research.kfg.Package, init: Pipeline.() -> Unit): Pipeline =
    PackagePipeline(cm, target).also {
        it.init()
    }

fun buildPipeline(cm: org.vorpal.research.kfg.ClassManager, targets: List<org.vorpal.research.kfg.Package>, init: Pipeline.() -> Unit): Pipeline =
    MultiplePackagePipeline(cm, targets).also {
        it.init()
    }

fun buildPipeline(cm: org.vorpal.research.kfg.ClassManager, target: Class, init: Pipeline.() -> Unit): Pipeline =
    ClassPipeline(cm, target).also {
        it.init()
    }

fun buildPipeline(cm: org.vorpal.research.kfg.ClassManager, targets: Collection<Method>, init: Pipeline.() -> Unit): Pipeline =
    MethodPipeline(cm, targets).also {
        it.init()
    }

fun executePipeline(cm: org.vorpal.research.kfg.ClassManager, target: org.vorpal.research.kfg.Package, init: Pipeline.() -> Unit) =
    buildPipeline(cm, target, init).run()

fun executePipeline(cm: org.vorpal.research.kfg.ClassManager, targets: List<org.vorpal.research.kfg.Package>, init: Pipeline.() -> Unit) =
    buildPipeline(cm, targets, init).run()

fun executePipeline(cm: org.vorpal.research.kfg.ClassManager, target: Class, init: Pipeline.() -> Unit) =
    buildPipeline(cm, target, init).run()

fun executePipeline(cm: org.vorpal.research.kfg.ClassManager, targets: Collection<Method>, init: Pipeline.() -> Unit) =
    buildPipeline(cm, targets, init).run()
