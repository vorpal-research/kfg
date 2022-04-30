package org.vorpal.research.kfg.ir

import org.objectweb.asm.tree.MethodNode
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
import org.vorpal.research.kthelper.defaultHashCode
import org.vorpal.research.kthelper.graph.GraphView
import org.vorpal.research.kthelper.graph.PredecessorGraph
import org.vorpal.research.kthelper.graph.Viewable

data class MethodDescriptor(
    val args: Array<out Type>,
    val returnType: Type
) {
    companion object {
        fun fromDesc(tf: TypeFactory, desc: String): MethodDescriptor {
            val (args, retval) = parseMethodDesc(tf, desc)
            return MethodDescriptor(args, retval)
        }
    }

    val asmDesc: String
        get() = "(${args.joinToString(separator = "") { it.asmDesc }})${returnType.asmDesc}"

    override fun hashCode() = defaultHashCode(*args, returnType)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as MethodDescriptor
        return this.args.contentEquals(other.args) && this.returnType == other.returnType
    }

    override fun toString() = "(${args.joinToString { it.name }}): ${returnType.name}"
}

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
            return innerBlocks.filter { it !in catches }.toList()
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
        (0 until innerBlocks.size)
            .filter { basicBlocks[it] == from }
            .forEach {
                innerBlocks[it].removeUser(this@MethodBody)
                innerBlocks[it] = to.get()
                to.addUser(this@MethodBody)
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

class Method : Node {
    val klass: Class
    internal val mn: MethodNode
    val desc: MethodDescriptor
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
    private var innerParameters = mutableListOf<Parameter>()
    private var innerExceptions = mutableSetOf<Class>()

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
        this.innerParameters.addAll(
            mn.parameters?.withIndex()?.map { (index, param) ->
                Parameter(cm, index, param.name, desc.args[index], Modifiers(param.access))
            } ?: listOf()
        )
        this.innerExceptions.addAll(mn.exceptions.map { cm[it] })
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
    val parameters get() = innerParameters
    val exceptions get() = innerExceptions

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

    override fun toString() = prototype

    override fun hashCode() = defaultHashCode(name, klass, desc)
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
