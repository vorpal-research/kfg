package org.vorpal.research.kfg.visitor

import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.Node
import org.vorpal.research.kthelper.collection.dequeOf

abstract class Pipeline(val cm: ClassManager, pipeline: List<NodeVisitor> = arrayListOf()) {
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
    cm: ClassManager,
    val target: Package,
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
    cm: ClassManager,
    private val targets: Collection<Package>,
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
    cm: ClassManager,
    initialTargets: Collection<Class>,
    pipeline: List<NodeVisitor> = arrayListOf()
) : Pipeline(cm, pipeline) {
    private val targets = mutableSetOf<Class>()

    init {
        val classQueue = dequeOf(initialTargets)
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

open class MethodPipeline(
    cm: ClassManager,
    val targets: Collection<Method>,
    pipeline: List<NodeVisitor> = arrayListOf()
) : Pipeline(cm, pipeline) {
    private val classTargets = targets.map { it.klass }.toMutableSet()
    override val pipeline = pipeline.map { it.methodWrap() }.toMutableList()

    @Suppress("MemberVisibilityCanBePrivate")
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

fun buildPackagePipeline(cm: ClassManager, target: Package, init: Pipeline.() -> Unit): Pipeline =
    PackagePipeline(cm, target).also {
        it.init()
    }

fun buildPackagePipeline(cm: ClassManager, targets: Collection<Package>, init: Pipeline.() -> Unit): Pipeline =
    MultiplePackagePipeline(cm, targets).also {
        it.init()
    }

fun buildClassPipeline(cm: ClassManager, target: Class, init: Pipeline.() -> Unit): Pipeline =
    buildClassPipeline(cm, listOf(target), init)

fun buildClassPipeline(cm: ClassManager, targets: Collection<Class>, init: Pipeline.() -> Unit): Pipeline =
    ClassPipeline(cm, targets).also {
        it.init()
    }

fun buildMethodPipeline(cm: ClassManager, target: Method, init: Pipeline.() -> Unit): Pipeline =
    buildMethodPipeline(cm, listOf(target), init)

fun buildMethodPipeline(cm: ClassManager, targets: Collection<Method>, init: Pipeline.() -> Unit): Pipeline =
    MethodPipeline(cm, targets).also {
        it.init()
    }

fun executePackagePipeline(cm: ClassManager, target: Package, init: Pipeline.() -> Unit) =
    buildPackagePipeline(cm, target, init).run()

@Suppress("unused")
fun executePackagePipeline(cm: ClassManager, targets: Collection<Package>, init: Pipeline.() -> Unit) =
    buildPackagePipeline(cm, targets, init).run()

fun executeClassPipeline(cm: ClassManager, target: Class, init: Pipeline.() -> Unit) =
    buildClassPipeline(cm, target, init).run()

fun executeClassPipeline(cm: ClassManager, targets: Collection<Class>, init: Pipeline.() -> Unit) =
    buildClassPipeline(cm, targets, init).run()

fun executeMethodPipeline(cm: ClassManager, target: Method, init: Pipeline.() -> Unit) =
    buildMethodPipeline(cm, target, init).run()

fun executeMethodPipeline(cm: ClassManager, targets: Collection<Method>, init: Pipeline.() -> Unit) =
    buildMethodPipeline(cm, targets, init).run()
