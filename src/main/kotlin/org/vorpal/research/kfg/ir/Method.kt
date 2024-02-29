package org.vorpal.research.kfg.ir

import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.ParameterNode
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.KfgException
import org.vorpal.research.kfg.builder.cfg.CfgBuilder
import org.vorpal.research.kfg.ir.value.BlockUsageContext
import org.vorpal.research.kfg.ir.value.BlockUser
import org.vorpal.research.kfg.ir.value.SlotTracker
import org.vorpal.research.kfg.ir.value.UsableBlock
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kfg.type.parseMethodDesc
import org.vorpal.research.kfg.util.jsrInlined
import org.vorpal.research.kthelper.KtException
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.collection.queueOf
import org.vorpal.research.kthelper.graph.GraphView
import org.vorpal.research.kthelper.graph.PredecessorGraph
import org.vorpal.research.kthelper.graph.Viewable

data class MethodDescriptor(
    val args: List<Type>,
    val returnType: Type
) {
    private val hash = args.hashCode() * 31 + returnType.hashCode()

    companion object {
        fun fromDesc(tf: TypeFactory, desc: String): MethodDescriptor {
            val (argTypes, returnType) = parseMethodDesc(tf, desc)
            return MethodDescriptor(argTypes, returnType)
        }
    }

    val asmDesc: String
        get() = "(${args.joinToString(separator = "") { it.asmDesc }})${returnType.asmDesc}"

    override fun hashCode() = hash
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as MethodDescriptor
        return this.args == other.args && this.returnType == other.returnType
    }

    override fun toString() = "(${args.joinToString { it.name }}): ${returnType.name}"
}

@Suppress("unused", "MemberVisibilityCanBePrivate")
class MethodBody(val method: Method) : PredecessorGraph<BasicBlock>, Iterable<BasicBlock>, BlockUser, Viewable {
    private val innerBlocks = arrayListOf<BasicBlock>()
    private val innerCatches = hashSetOf<CatchBlock>()
    val basicBlocks: List<BasicBlock> get() = innerBlocks
    val catchEntries: Set<CatchBlock> get() = innerCatches
    val slotTracker = SlotTracker(this)

    override val entry: BasicBlock
        get() = innerBlocks.first { it is BodyBlock && it.predecessors.isEmpty() }

    val bodyBlocks: List<BasicBlock>
        get() {
            val catches = catchBlocks
            return innerBlocks.filter { it !in catches }
        }

    val catchBlocks: List<BasicBlock>
        get() {
            val catchMap = hashMapOf<BasicBlock, Boolean>()
            val visited = hashSetOf<BasicBlock>()
            val result = arrayListOf<BasicBlock>()
            val queue = queueOf<BasicBlock>()
            queue.addAll(catchEntries)
            while (queue.isNotEmpty()) {
                val top = queue.poll()
                val isCatch = top.predecessors.fold(true) { acc, bb -> acc && catchMap.getOrPut(bb) { false } }
                if (isCatch && top !in visited) {
                    result.add(top)
                    queue.addAll(top.successors)
                    catchMap[top] = true
                    visited += top
                }
            }
            return result
        }

    override val nodes: Set<BasicBlock>
        get() = innerBlocks.toSet()

    fun isEmpty() = innerBlocks.isEmpty()
    fun isNotEmpty() = !isEmpty()

    internal fun clear() {
        innerBlocks.clear()
        innerCatches.clear()
    }

    fun add(ctx: BlockUsageContext, bb: BasicBlock) = with(ctx) {
        if (bb !in innerBlocks) {
            ktassert(!bb.hasParent, "Block ${bb.name} already belongs to other method")
            innerBlocks.add(bb)
            slotTracker.addBlock(bb)
            bb.addUser(this@MethodBody)
            bb.parentUnsafe = this@MethodBody
        }
    }

    fun addBefore(ctx: BlockUsageContext, before: BasicBlock, bb: BasicBlock) = with(ctx) {
        if (bb !in innerBlocks) {
            ktassert(!bb.hasParent, "Block ${bb.name} already belongs to other method")
            val index = basicBlocks.indexOf(before)
            ktassert(index >= 0, "Block ${before.name} does not belong to method ${method.prototype}")

            innerBlocks.add(index, bb)
            slotTracker.addBlock(bb)
            bb.addUser(this@MethodBody)
            bb.parentUnsafe = this@MethodBody
        }
    }

    fun addAfter(ctx: BlockUsageContext, after: BasicBlock, bb: BasicBlock) = with(ctx) {
        if (bb !in innerBlocks) {
            ktassert(!bb.hasParent, "Block ${bb.name} already belongs to other method")
            val index = basicBlocks.indexOf(after)
            ktassert(index >= 0, "Block ${after.name} does not belong to method ${method.prototype}")

            innerBlocks.add(index + 1, bb)
            slotTracker.addBlock(bb)
            bb.addUser(this@MethodBody)
            bb.parentUnsafe = this@MethodBody
        }
    }

    fun remove(ctx: BlockUsageContext, block: BasicBlock) = with(ctx) {
        if (innerBlocks.contains(block)) {
            ktassert(block.parentUnsafe == this@MethodBody, "Block ${block.name} don't belong to ${method.prototype}")
            innerBlocks.remove(block)

            if (block in innerCatches) {
                innerCatches.remove(block)
            }

            block.removeUser(this@MethodBody)
            block.parentUnsafe = null
            slotTracker.removeBlock(block)
        }
    }

    fun addCatchBlock(bb: CatchBlock) {
        require(bb in innerBlocks)
        innerCatches.add(bb)
    }

    fun getNext(from: BasicBlock): BasicBlock {
        val start = innerBlocks.indexOf(from)
        return innerBlocks[start + 1]
    }

    fun getBlockByLocation(location: Location) = innerBlocks.find { it.location == location }
    fun getBlockByName(name: String) = innerBlocks.find { it.name.toString() == name }

    fun print() = buildString {
        append(innerBlocks.joinToString(separator = "\n\n") { "$it" })
    }

    override fun toString() = print()
    override fun iterator() = innerBlocks.iterator()

    override fun hashCode() = method.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as MethodBody
        return this.method == other.method
    }

    override fun replaceUsesOf(ctx: BlockUsageContext, from: UsableBlock, to: UsableBlock) = with(ctx) {
        for (index in innerBlocks.indices) {
            if (basicBlocks[index] == from) {
                innerBlocks[index].removeUser(this@MethodBody)
                innerBlocks[index] = to.get()
                to.addUser(this@MethodBody)
            }
        }
    }

    override fun clearBlockUses(ctx: BlockUsageContext) = with(ctx) {
        innerBlocks.forEach {
            it.removeUser(this@MethodBody)
        }
    }

    override val graphView: List<GraphView>
        get() {
            val nodes = hashMapOf<String, GraphView>()
            nodes[method.name] = GraphView(method.name, method.prototype)

            for (bb in basicBlocks) {
                val label = StringBuilder()
                label.append("${bb.name}: ${bb.predecessors.joinToString(", ") { it.name.toString() }}\\l")
                bb.instructions.forEach { label.append("    ${it.print().replace("\"", "\\\"")}\\l") }
                nodes[bb.name.toString()] = GraphView(bb.name.toString(), label.toString())
            }

            if (!method.isAbstract) {
                val entryNode = nodes.getValue(entry.name.toString())
                nodes.getValue(method.name).addSuccessor(entryNode)
            }

            for (it in basicBlocks) {
                val current = nodes.getValue(it.name.toString())
                for (successor in it.successors) {
                    current.addSuccessor(nodes.getValue(successor.name.toString()))
                }
            }

            return nodes.values.toList()
        }
}

@Suppress("unused")
class Method : Node {
    val klass: Class
    internal val mn: MethodNode
    var bodyInitialized: Boolean = false
        private set

    val body: MethodBody by lazy {
        bodyInitialized = true
        try {
            if (!isAbstract) CfgBuilder(cm, this).build()
            else MethodBody(this)
        } catch (e: KfgException) {
            if (cm.failOnError) throw e
            MethodBody(this)
        } catch (e: KtException) {
            if (cm.failOnError) throw e
            MethodBody(this)
        }
    }

    private var descInternal: MethodDescriptor? = null
        set(value) {
            field?.let { klass.updateMethod(it, value!!, this) }
            field = value
        }

    var desc: MethodDescriptor
        get() = descInternal!!
        set(value) {
            descInternal = value
        }
    var parameters = listOf<Parameter>()
    var exceptions = setOf<Class>()

    override val innerAnnotations = mutableSetOf<Annotation>()
    override val innerTypeAnnotations = mutableSetOf<TypeAnnotation>()

    companion object {
        const val CONSTRUCTOR_NAME = "<init>"
        const val STATIC_INIT_NAME = "<clinit>"
    }

    constructor(
        cm: ClassManager,
        klass: Class,
        node: MethodNode
    ) : super(cm, node.name, Modifiers(node.access)) {
        this.klass = klass
        this.mn = node.jsrInlined
        this.desc = MethodDescriptor.fromDesc(cm.type, node.desc)
        this.parameters = getParameters(cm, mn, argTypes)
        this.exceptions = mn.exceptions.mapTo(mutableSetOf()) { cm[it] }
        node.visibleAnnotations.orEmpty().filterNotNull().forEach {
            innerAnnotations += AnnotationBase.parseAnnotation(cm, it, visible = true)
        }
        node.invisibleAnnotations.orEmpty().filterNotNull().forEach {
            innerAnnotations += AnnotationBase.parseAnnotation(cm, it, visible = false)
        }
        node.visibleTypeAnnotations.orEmpty().filterNotNull().forEach {
            innerTypeAnnotations += AnnotationBase.parseTypeAnnotation(cm, it, visible = true)
        }
        node.invisibleTypeAnnotations.orEmpty().filterNotNull().forEach {
            innerTypeAnnotations += AnnotationBase.parseTypeAnnotation(cm, it, visible = false)
        }
    }

    constructor(
        cm: ClassManager,
        klass: Class,
        name: String,
        desc: MethodDescriptor,
        modifiers: Modifiers = Modifiers(0)
    ) : super(cm, name, modifiers) {
        this.klass = klass
        this.mn = MethodNode(modifiers.value, name, desc.asmDesc, null, null)
        this.desc = desc
    }

    val argTypes get() = desc.args
    val returnType get() = desc.returnType

    val prototype: String
        get() = "$klass::$name$desc"

    val isConstructor: Boolean
        get() = name == CONSTRUCTOR_NAME

    val isStaticInitializer: Boolean
        get() = name == STATIC_INIT_NAME

    override val asmDesc
        get() = desc.asmDesc

    val hasBody get() = body.isNotEmpty()
    val hasLoops get() = cm.loopManager.getMethodLoopInfo(this).isNotEmpty()

    fun getLoopInfo() = cm.loopManager.getMethodLoopInfo(this)
    fun invalidateLoopInfo() = cm.loopManager.setInvalid(this)

    fun print() = buildString {
        appendLine(prototype)
        if (bodyInitialized) append(body.print())
    }


    public override fun addAnnotation(annotation: Annotation) {
        super.addAnnotation(annotation)
    }

    public override fun addTypeAnnotation(annotation: TypeAnnotation) {
        super.addTypeAnnotation(annotation)
    }

    public override fun removeAnnotation(annotation: Annotation) {
        super.removeAnnotation(annotation)
    }

    public override fun removeTypeAnnotation(annotation: TypeAnnotation) {
        super.removeTypeAnnotation(annotation)
    }

    override fun toString() = prototype

//    override fun hashCode() = defaultHashCode(name, klass, desc)

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + klass.hashCode()
        result = 31 * result + desc.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Method
        return this.name == other.name && this.klass == other.klass && this.desc == other.desc
    }

    fun view(dot: String, viewer: String) {
        body.view(name, dot, viewer)
    }
}


private fun getParameters(cm: ClassManager, methodNode: MethodNode, argTypes: List<Type>): List<Parameter> =
    processNodeParameters(cm, methodNode, argTypes, argTypes.indices.map {
        methodNode.parameters.orEmpty().getOrNull(it)
    })

private fun processNodeParameters(
    cm: ClassManager,
    methodNode: MethodNode,
    argTypes: List<Type>,
    parameters: List<ParameterNode?>,
): List<Parameter> =
    buildList {
        val visibleParameterAnnotations = methodNode.visibleParameterAnnotations ?: arrayOfNulls(parameters.size)
        val invisibleParameterAnnotations = methodNode.invisibleParameterAnnotations ?: arrayOfNulls(parameters.size)

        for ((index, param) in parameters.withIndex()) {
            val visibleAnnotationsNodes = visibleParameterAnnotations[index].orEmpty()
            val invisibleAnnotationNodes = invisibleParameterAnnotations[index].orEmpty()

            val visibleAnnotations = visibleAnnotationsNodes.mapTo(mutableSetOf()) {
                AnnotationBase.parseAnnotation(cm, it, visible = true)
            }
            val invisibleAnnotations = invisibleAnnotationNodes.mapTo(mutableSetOf()) {
                AnnotationBase.parseAnnotation(cm, it, visible = false)
            }

            add(
                Parameter(
                    cm,
                    index,
                    param?.name ?: Parameter.STUB_NAME,
                    argTypes[index],
                    Modifiers(param?.access ?: 0),
                    visibleAnnotations + invisibleAnnotations
                )
            )
        }
    }
