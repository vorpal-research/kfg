package org.vorpal.research.kfg.visitor

import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.Node
import org.vorpal.research.kfg.visitor.pass.AnalysisManager
import org.vorpal.research.kfg.visitor.pass.AnalysisVisitor
import org.vorpal.research.kfg.visitor.pass.PassManager
import org.vorpal.research.kthelper.collection.dequeOf
import org.vorpal.research.kthelper.logging.log

abstract class Pipeline(val cm: ClassManager, pipeline: List<NodeVisitor> = arrayListOf()) {
    var passManager = PassManager()
    val analysisManager: AnalysisManager by lazy { AnalysisManager(cm, this@Pipeline) }
    val visitorRegistry = VisitorRegistry()

    protected open val runnablePipeline: List<NodeVisitor>
        get() = passManager.getPassOrder(this).map { it.wrap() }

    // List of all scheduled passes. Used to add all passes only single time
    private val scheduled = mutableSetOf<java.lang.Class<*>>()
    private val passesToRun: MutableList<NodeVisitor> = pipeline.toMutableList()
    private var previousDirectlyAddedVisitor: NodeVisitor? = null

    fun <T : NodeVisitor> schedule(visitor: java.lang.Class<T>) {
        if (!scheduled.add(visitor)) {
            return
        }

        val visitorInstance = try {visitor.getConstructor(ClassManager::class.java, Pipeline::class.java)
                .apply { isAccessible = true }
                .newInstance(cm, this@Pipeline)
        } catch (e: NoSuchMethodException) {
            log.warn("Tried to schedule visitor ${visitor.name}, but not required constructor found. Assuming user will add an instance manually")
            return
        }

        processScheduledInstance(visitorInstance)
    }

    fun schedule(visitorInstance: NodeVisitor, shouldPersistOrder: Boolean) {
        if (!scheduled.add(visitorInstance::class.java)) {
            return
        }

        if (shouldPersistOrder && previousDirectlyAddedVisitor != null) {
            visitorRegistry.addRequiredPass(visitorInstance::class.java, previousDirectlyAddedVisitor!!::class.java)
        }
        if (shouldPersistOrder) {
            previousDirectlyAddedVisitor = visitorInstance
        }

        processScheduledInstance(visitorInstance)
    }

    private fun processScheduledInstance(visitorInstance: NodeVisitor) {
        visitorInstance.registerPassDependencies()
        visitorInstance.registerAnalysisDependencies()

        passesToRun.add(visitorInstance)

        visitorRegistry.getVisitorDependencies(visitorInstance::class.java).forEach { schedule(it) }

        fun registerAnalysisDependencies(analysis: java.lang.Class<out AnalysisVisitor<*>>) {
            if (visitorRegistry.getAnalysisDependencies(analysis).isNotEmpty()) return

            analysisManager.getVisitorInstance(analysis).registerAnalysisDependencies()
            visitorRegistry.getAnalysisDependencies(analysis).forEach {
                registerAnalysisDependencies(it)
            }
        }
        visitorRegistry.getAnalysisDependencies(visitorInstance::class.java).forEach { registerAnalysisDependencies(it) }
    }

    fun registerProvider(provider: KfgProvider<*>) {
        visitorRegistry.registerProvider(provider)
    }

    fun getPasses() = passesToRun.map { it }

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
        schedule(this, true)
    }

    operator fun KfgProvider<*>.unaryPlus() {
        registerProvider(this)
    }

    fun run() {
        passManager.verify(this)
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
        val classQueue = dequeOf(target)
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

open class MethodPipeline(
    cm: ClassManager,
    val targets: Collection<Method>,
    pipeline: List<NodeVisitor> = arrayListOf()
) : Pipeline(cm, pipeline) {
    override val runnablePipeline: List<NodeVisitor>
        get() = passManager.getPassOrder(this).map { it.methodWrap() }.toList()

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

    override fun runInternal() {
        for (pass in runnablePipeline) {
            for (`class` in classTargets) {
                (pass as ClassVisitor).visit(`class`)
            }
        }
    }
}

fun pipelineStub() = PipelineStub()

class PipelineStub : Pipeline(ClassManager()) {
    override fun runInternal() {
        // Do nothing
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
    buildPipeline(cm, target, init).apply { run() }

fun executePipeline(cm: ClassManager, targets: List<Package>, init: Pipeline.() -> Unit) =
    buildPipeline(cm, targets, init).apply { run() }

fun executePipeline(cm: ClassManager, target: Class, init: Pipeline.() -> Unit) =
    buildPipeline(cm, target, init).apply { run() }

fun executePipeline(cm: ClassManager, targets: Collection<Method>, init: Pipeline.() -> Unit) =
    buildPipeline(cm, targets, init).apply { run() }
