package org.jetbrains.research.kfg.visitor

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.Node
import org.jetbrains.research.kfg.visitor.pass.AnalysisManager
import org.jetbrains.research.kfg.visitor.pass.PassManager
import java.util.*

abstract class Pipeline(val cm: ClassManager, pipeline: List<NodeVisitor> = arrayListOf()) {
    var passManager = PassManager()
    val analysisManager: AnalysisManager by lazy {
        AnalysisManager(cm, this@Pipeline)
    }
    val visitorRegistry = VisitorRegistry()

    protected val pipeline: MutableList<NodeVisitor> = pipeline.toMutableList()
    protected open var runnablePipeline = listOf<NodeVisitor>()
    // List of all scheduled passes. Used to add all passes only single time
    protected open val scheduled = mutableSetOf<java.lang.Class<*>>()
    protected val passOrder get() = passManager.getPassOrder(this@Pipeline)

    fun <T : NodeVisitor> schedule(visitor: java.lang.Class<T>) {
        if (scheduled.contains(visitor)) {
            return
        }

        val visitorInstance = visitor.getConstructor(ClassManager::class.java, Pipeline::class.java)
                .apply { isAccessible = true }
                .newInstance(cm, this@Pipeline)

        add(visitorInstance)

        visitorRegistry.getVisitorDependencies(visitor).forEach {
            schedule(it)
        }
    }

    fun registerProvider(provider: KfgProvider<*>) {
        visitorRegistry.registerProvider(provider)
    }

    fun add(visitor: NodeVisitor): Boolean {
        if (!scheduled.add(visitor::class.java)) {
            return false
        }

        visitor.registerPassDependencies()
        visitor.registerAnalysisDependencies()

        return pipeline.add(visitor)
    }

    @Suppress("UNCHECKED_CAST")
    fun getPasses() = pipeline.map { it }

    protected fun NodeVisitor.wrap(): ClassVisitor = when (val visitor = this) {
        is ClassVisitor -> visitor
        is MethodVisitor -> object : ClassVisitor, VisitorWrapper {
            override val cm get() = this@Pipeline.cm
            override val pipeline get() = this@Pipeline

            override fun cleanup() {
                visitor.cleanup()
            }

            override fun registerPassDependencies() {
                visitor.registerPassDependencies()
            }

            override fun registerAnalysisDependencies() {
                visitor.registerAnalysisDependencies()
            }

            override fun visitMethod(method: Method) {
                super.visitMethod(method)
                visitor.visit(method)
            }

            override val wrapped: NodeVisitor
                get() = visitor
        }
        else -> object : ClassVisitor, VisitorWrapper {
            override val cm get() = this@Pipeline.cm
            override val pipeline get() = this@Pipeline

            override fun cleanup() {
                visitor.cleanup()
            }

            override fun registerPassDependencies() {
                visitor.registerPassDependencies()
            }

            override fun registerAnalysisDependencies() {
                visitor.registerAnalysisDependencies()
            }

            override fun visit(node: Node) {
                super.visit(node)
                visitor.visit(node)
            }

            override val wrapped: NodeVisitor
                get() = visitor
        }
    }

    operator fun NodeVisitor.unaryPlus() {
        schedule(this.javaClass)
    }

    protected open fun buildRunnablePipeline(): List<NodeVisitor> {
        return passOrder.map { it.wrap() }
    }

    fun run() {
        runnablePipeline = buildRunnablePipeline()
        runInternal()
    }

    protected abstract fun runInternal()
}

class PackagePipeline(
    cm: ClassManager,
    val target: Package,
    pipeline: List<NodeVisitor> = arrayListOf()
) : Pipeline(cm, pipeline) {
    override fun runInternal() {
        val classes = cm.getByPackage(target)
        for (pass in runnablePipeline) {
            for (`class` in classes) {
                (pass as ClassVisitor).visit(`class`)
            }
        }
    }
}

class MultiplePackagePipeline(
    cm: ClassManager,
    val targets: List<Package>,
    pipeline: List<NodeVisitor> = arrayListOf()
) : Pipeline(cm, pipeline) {
    override fun runInternal() {
        val classes = targets.flatMap { cm.getByPackage(it) }
        for (pass in runnablePipeline) {
            for (`class` in classes) {
                (pass as ClassVisitor).visit(`class`)
            }
        }
    }
}

class ClassPipeline(
    cm: ClassManager,
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

    override fun runInternal() {
        for (pass in runnablePipeline) {
            for (`class` in targets) {
                (pass as ClassVisitor).visit(`class`)
            }
        }
    }
}

class MethodPipeline(
    cm: ClassManager,
    val targets: Collection<Method>,
    pipeline: List<NodeVisitor> = arrayListOf()
) : Pipeline(cm, pipeline) {
    private val classTargets = targets.map { it.klass }.toMutableSet()

    protected fun NodeVisitor.methodWrap(): ClassVisitor = when (val visitor = this) {
        is ClassVisitor -> object : ClassVisitor, VisitorWrapper {
            override val cm get() = this@MethodPipeline.cm
            override val pipeline get() = this@MethodPipeline

            override fun cleanup() {
                visitor.cleanup()
            }

            override fun registerPassDependencies() {
                visitor.registerPassDependencies()
            }

            override fun registerAnalysisDependencies() {
                visitor.registerAnalysisDependencies()
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

            override val wrapped: NodeVisitor
                get() = visitor
        }
        is MethodVisitor -> object : ClassVisitor, VisitorWrapper {
            override val cm get() = this@MethodPipeline.cm
            override val pipeline get() = this@MethodPipeline

            override fun cleanup() {
                visitor.cleanup()
            }

            override fun registerPassDependencies() {
                visitor.registerPassDependencies()
            }

            override fun registerAnalysisDependencies() {
                visitor.registerAnalysisDependencies()
            }

            override fun visitMethod(method: Method) {
                if (method in targets) {
                    super.visitMethod(method)
                    visitor.visit(method)
                }
            }

            override val wrapped: NodeVisitor
                get() = visitor
        }
        else -> this.wrap()
    }

    override fun buildRunnablePipeline(): List<NodeVisitor> {
        return passOrder.map { it.methodWrap() }.toList()
    }

    override fun runInternal() {
        for (pass in runnablePipeline) {
            for (`class` in classTargets) {
                (pass as ClassVisitor).visit(`class`)
            }
        }
    }
}

fun buildPipeline(cm: ClassManager, target: Package, init: Pipeline.() -> Unit): Pipeline =
    PackagePipeline(cm, target).also {
        it.init()
    }

fun buildPipeline(cm: ClassManager, targets: List<Package>, init: Pipeline.() -> Unit): Pipeline =
    MultiplePackagePipeline(cm, targets).also {
        it.init()
    }

fun buildPipeline(cm: ClassManager, target: Class, init: Pipeline.() -> Unit): Pipeline =
    ClassPipeline(cm, target).also {
        it.init()
    }

fun buildPipeline(cm: ClassManager, targets: Collection<Method>, init: Pipeline.() -> Unit): Pipeline =
    MethodPipeline(cm, targets).also {
        it.init()
    }

fun executePipeline(cm: ClassManager, target: Package, init: Pipeline.() -> Unit) =
    buildPipeline(cm, target, init).run()

fun executePipeline(cm: ClassManager, targets: List<Package>, init: Pipeline.() -> Unit) =
    buildPipeline(cm, targets, init).run()

fun executePipeline(cm: ClassManager, target: Class, init: Pipeline.() -> Unit) =
    buildPipeline(cm, target, init).run()

fun executePipeline(cm: ClassManager, targets: Collection<Method>, init: Pipeline.() -> Unit) =
    buildPipeline(cm, targets, init).run()
