package org.jetbrains.research.kfg.visitor

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.Node
import org.jetbrains.research.kfg.visitor.pass.AnalysisManager
import org.jetbrains.research.kfg.visitor.pass.AnalysisVisitor
import org.jetbrains.research.kfg.visitor.pass.PassManager
import org.jetbrains.research.kfg.visitor.pass.PassOrder
import java.util.*

abstract class Pipeline(val cm: ClassManager, pipeline: List<NodeVisitor> = arrayListOf()) {
    val passManager = PassManager()
    val analysisManager: AnalysisManager by lazy {
        AnalysisManager(cm, this@Pipeline)
    }

    protected open val pipeline = pipeline.map { it.wrap() }.toMutableList()
    protected val passOrder get() = passManager.getPassOrder(this@Pipeline)

    fun <T : NodeVisitor> add(visitor: java.lang.Class<T>, vararg additionalArgs: Any?) {
        val visitorInstance = visitor.getConstructor(ClassManager::class.java,
                    Pipeline::class.java,
                    *additionalArgs.map { it?.javaClass ?: Object::class.java }.toTypedArray()
                )
                .apply { isAccessible = true }
                .newInstance(cm, this@Pipeline, *additionalArgs)
        add(visitorInstance)
    }

    fun add(visitorName: String) {
        add(VisitorRegistry.getVisitor(visitorName)!!.invoke(cm, this@Pipeline) as NodeVisitor)
    }

    operator fun plus(visitor: NodeVisitor) = add(visitor)
    operator fun plusAssign(visitor: NodeVisitor) {
        add(visitor)
    }

    open fun add(visitor: NodeVisitor) = pipeline.add(visitor.wrap())
    fun add(vararg visitors: NodeVisitor) {
        visitors.forEach { add(it) }
    }

    @Suppress("UNCHECKED_CAST")
    fun getPasses() = pipeline.map { it }

    protected fun NodeVisitor.wrap(): ClassVisitor = when (val visitor = this) {
        is ClassVisitor -> visitor
        is MethodVisitor -> object : ClassVisitor {
            override val cm get() = this@Pipeline.cm
            override val pipeline get() = this@Pipeline

            override fun cleanup() {
                visitor.cleanup()
            }

            override fun getName(): String {
                return visitor.getName()
            }

            override fun getRequiredAnalysisVisitors(): List<String> =
                    visitor.getRequiredAnalysisVisitors()

            override fun getPersistedAnalysisVisitors(): List<String> =
                    visitor.getPersistedAnalysisVisitors()

            override fun getRequiredPasses(): List<String> =
                    visitor.getRequiredPasses()

            override fun visitMethod(method: Method) {
                super.visitMethod(method)
                visitor.visit(method)
            }
        }
        else -> object : ClassVisitor {
            override val cm get() = this@Pipeline.cm
            override val pipeline get() = this@Pipeline

            override fun cleanup() {
                visitor.cleanup()
            }

            override fun getName(): String {
                return visitor.getName()
            }

            override fun getRequiredAnalysisVisitors(): List<String> =
                    visitor.getRequiredAnalysisVisitors()

            override fun getPersistedAnalysisVisitors(): List<String> =
                    visitor.getPersistedAnalysisVisitors()

            override fun getRequiredPasses(): List<String> =
                    visitor.getRequiredPasses()

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
        for (pass in passOrder) {
            for (`class` in classes) {
                (pass as ClassVisitor).visit(`class`)
                analysisManager.invalidateAllExcept(
                        pass.getPersistedAnalysisVisitors(),
                        `class`
                )
            }
        }
    }
}

class MultiplePackagePipeline(
    cm: ClassManager,
    val targets: List<Package>,
    pipeline: List<NodeVisitor> = arrayListOf()
) : Pipeline(cm, pipeline) {
    override fun run() {
        val classes = targets.flatMap { cm.getByPackage(it) }
        for (pass in passOrder) {
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

    override fun run() {
        for (pass in passOrder) {
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
    override val pipeline = pipeline.map { it.methodWrap() }.toMutableList()

    protected fun NodeVisitor.methodWrap(): ClassVisitor = when (val visitor = this) {
        is ClassVisitor -> object : ClassVisitor {
            override val cm get() = this@MethodPipeline.cm
            override val pipeline get() = this@MethodPipeline

            override fun cleanup() {
                visitor.cleanup()
            }

            override fun getName(): String {
                return visitor.getName()
            }

            override fun getRequiredAnalysisVisitors(): List<String> =
                    visitor.getRequiredAnalysisVisitors()

            override fun getPersistedAnalysisVisitors(): List<String> =
                    visitor.getPersistedAnalysisVisitors()

            override fun getRequiredPasses(): List<String> =
                    visitor.getRequiredPasses()

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
            override val pipeline get() = this@MethodPipeline

            override fun cleanup() {
                visitor.cleanup()
            }

            override fun getName(): String {
                return visitor.getName()
            }

            override fun getRequiredAnalysisVisitors(): List<String> =
                    visitor.getRequiredAnalysisVisitors()

            override fun getPersistedAnalysisVisitors(): List<String> =
                    visitor.getPersistedAnalysisVisitors()

            override fun getRequiredPasses(): List<String> =
                    visitor.getRequiredPasses()

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
        for (pass in passOrder) {
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
