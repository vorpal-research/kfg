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

private typealias JavaClass<T> = java.lang.Class<T>

abstract class Pipeline(val cm: ClassManager, pipeline: List<NodeVisitor> = arrayListOf()) {
    var passManager = PassManager()
    val analysisManager: AnalysisManager by lazy { AnalysisManager(cm, this@Pipeline) }
    internal val internalVisitorRegistry = InternalVisitorRegistry()
    val visitorRegistry get() = internalVisitorRegistry.exposed

    protected open val runnablePipeline: List<NodeVisitor>
        get() = passManager.getPassOrder(this).map { it.wrap() }

    // List of all scheduled passes. Used to add all passes only single time
    private val scheduled = mutableSetOf<JavaClass<*>>()
    private val passesToRun: MutableList<NodeVisitor> = pipeline.toMutableList()
    private var previousDirectlyAddedVisitor: NodeVisitor? = null

    val passes: List<NodeVisitor> get() = passesToRun

    fun schedule(visitor: JavaClass<out NodeVisitor>, shouldPersistOrder: Boolean) {
        val visitorInstance = getVisitorInstance(visitor) ?: return
        processScheduledInstance(visitorInstance, shouldPersistOrder)
    }

    fun schedule(visitorInstance: NodeVisitor, shouldPersistOrder: Boolean) {
        processScheduledInstance(visitorInstance, shouldPersistOrder)
    }

    private fun processScheduledInstance(visitorInstance: NodeVisitor, shouldPersistOrder: Boolean) {
        if (!scheduled.add(visitorInstance::class.java)) {
            return
        }

        if (shouldPersistOrder && previousDirectlyAddedVisitor != null) {
            internalVisitorRegistry.addRequiredPass(visitorInstance::class.java, previousDirectlyAddedVisitor!!::class.java)
        }
        if (shouldPersistOrder) {
            previousDirectlyAddedVisitor = visitorInstance
        }

        visitorInstance.registerPassDependencies()
        visitorInstance.registerAnalysisDependencies()

        passesToRun.add(visitorInstance)

        internalVisitorRegistry.getVisitorDependencies(visitorInstance::class.java).forEach { schedule(it, false) }

        fun registerAnalysisDependencies(analysis: JavaClass<out AnalysisVisitor<*>>) {
            if (internalVisitorRegistry.getAnalysisDependencies(analysis).isNotEmpty()) return

            analysisManager.getVisitorInstance(analysis).registerAnalysisDependencies()
            internalVisitorRegistry.getAnalysisDependencies(analysis).forEach {
                registerAnalysisDependencies(it)
            }
        }
        internalVisitorRegistry.getAnalysisDependencies(visitorInstance::class.java).forEach { registerAnalysisDependencies(it) }
    }

    fun registerProvider(provider: KfgProvider) {
        internalVisitorRegistry.registerProvider(provider)
    }

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

    fun NodeVisitor.schedule() {
        schedule(this, false)
    }

    fun NodeVisitor.scheduleOrdered() {
        schedule(this, true)
    }

    operator fun NodeVisitor.unaryPlus() {
        this.scheduleOrdered()
    }

    operator fun KfgProvider.unaryPlus() {
        registerProvider(this)
    }

    fun run() {
        passManager.verify(this)
        runInternal()
    }

    protected abstract fun runInternal()

    private fun <T : NodeVisitor> getVisitorInstance(visitor: JavaClass<T>) =
        try {
            visitor.getConstructor(ClassManager::class.java, Pipeline::class.java)
                .apply { isAccessible = true }
                .newInstance(cm, this@Pipeline)
        } catch (e: NoSuchMethodException) {
            // Tried to schedule visitor ${visitor.name}, but not required constructor found. Assuming user will add an instance manually
            // If not - the pass manager will throw a dependency validation exception
            null
        }
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

val memoizedPipelineStub = PipelineStub()

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
