package org.vorpal.research.kfg.ir

import org.objectweb.asm.tree.MethodNode
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.value.*
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kfg.type.parseMethodDesc
import org.vorpal.research.kfg.util.jsrInlined
import org.vorpal.research.kthelper.assert.ktassert
import org.vorpal.research.kthelper.collection.queueOf
import org.vorpal.research.kthelper.defaultHashCode
import org.vorpal.research.kthelper.graph.GraphView
import org.vorpal.research.kthelper.graph.PredecessorGraph
import org.vorpal.research.kthelper.graph.Viewable

data class MethodDesc(
    val args: Array<out Type>,
    val returnType: Type
) {
    companion object {
        fun fromDesc(tf: TypeFactory, desc: String): MethodDesc {
            val (args, retval) = parseMethodDesc(tf, desc)
            return MethodDesc(args, retval)
        }
    }

    val asmDesc: String
        get() = "(${args.joinToString(separator = "") { it.asmDesc }})${returnType.asmDesc}"

    override fun hashCode() = defaultHashCode(*args, returnType)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as MethodDesc
        return this.args.contentEquals(other.args) && this.returnType == other.returnType
    }

    override fun toString() = "(${args.joinToString { it.name }}): ${returnType.name}"
}

class Method : Node, PredecessorGraph<BasicBlock>, Iterable<BasicBlock>, BlockUser, Viewable {
    val klass: Class
    internal val mn: MethodNode
    val desc: MethodDesc
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
        this.desc = MethodDesc.fromDesc(cm.type, node.desc)
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
        desc: MethodDesc,
        modifiers: Modifiers = Modifiers(0)
    ) : super(cm, name, modifiers) {
        this.klass = klass
        this.mn = MethodNode(modifiers.value, name, desc.asmDesc, null, null)
        this.desc = desc
    }

    val argTypes get() = desc.args
    val returnType get() = desc.returnType
    private val innerBlocks = arrayListOf<BasicBlock>()
    private val innerCatches = hashSetOf<CatchBlock>()
    val parameters get() = innerParameters
    val exceptions get() = innerExceptions
    val basicBlocks: List<BasicBlock> get() = innerBlocks
    val catchEntries: Set<CatchBlock> get() = innerCatches
    val slotTracker = SlotTracker(this)

    override val entry: BasicBlock
        get() = innerBlocks.first { it is BodyBlock && it.predecessors.isEmpty() }

    val prototype: String
        get() = "$klass::$name$desc"

    val isConstructor: Boolean
        get() = name == CONSTRUCTOR_NAME

    val isStaticInitializer: Boolean
        get() = name == STATIC_INIT_NAME

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

    override val asmDesc
        get() = desc.asmDesc

    override val nodes: Set<BasicBlock>
        get() = innerBlocks.toSet()

    val hasBody get() = this !in klass.failingMethods && isNotEmpty()
    val hasLoops get() = cm.loopManager.getMethodLoopInfo(this).isNotEmpty()

    fun getLoopInfo() = cm.loopManager.getMethodLoopInfo(this)
    fun invalidateLoopInfo() = cm.loopManager.setInvalid(this)

    fun isEmpty() = innerBlocks.isEmpty()
    fun isNotEmpty() = !isEmpty()

    internal fun clear() {
        invalidateLoopInfo()
        innerBlocks.clear()
        innerCatches.clear()
    }

    fun add(ctx: BlockUsageContext, bb: BasicBlock) = with(ctx) {
        if (bb !in innerBlocks) {
            ktassert(!bb.hasParent, "Block ${bb.name} already belongs to other method")
            innerBlocks.add(bb)
            slotTracker.addBlock(bb)
            bb.addUser(this@Method)
            bb.parentUnsafe = this@Method
        }
    }

    fun addBefore(ctx: BlockUsageContext, before: BasicBlock, bb: BasicBlock) = with(ctx) {
        if (bb !in innerBlocks) {
            ktassert(!bb.hasParent, "Block ${bb.name} already belongs to other method")
            val index = basicBlocks.indexOf(before)
            ktassert(index >= 0, "Block ${before.name} does not belong to method $prototype")

            innerBlocks.add(index, bb)
            slotTracker.addBlock(bb)
            bb.addUser(this@Method)
            bb.parentUnsafe = this@Method
        }
    }

    fun addAfter(ctx: BlockUsageContext, after: BasicBlock, bb: BasicBlock) = with(ctx) {
        if (bb !in innerBlocks) {
            ktassert(!bb.hasParent, "Block ${bb.name} already belongs to other method")
            val index = basicBlocks.indexOf(after)
            ktassert(index >= 0, "Block ${after.name} does not belong to method $prototype")

            innerBlocks.add(index + 1, bb)
            slotTracker.addBlock(bb)
            bb.addUser(this@Method)
            bb.parentUnsafe = this@Method
        }
    }

    fun remove(ctx: BlockUsageContext, block: BasicBlock) = with(ctx) {
        if (innerBlocks.contains(block)) {
            ktassert(block.parentUnsafe == this@Method, "Block ${block.name} don't belong to $prototype")
            innerBlocks.remove(block)

            if (block in innerCatches) {
                innerCatches.remove(block)
            }

            block.removeUser(this@Method)
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
        appendLine(prototype)
        append(basicBlocks.joinToString(separator = "\n\n") { "$it" })
    }

    override fun toString() = prototype
    override fun iterator() = innerBlocks.iterator()

    override fun hashCode() = defaultHashCode(name, klass, desc)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Method
        return this.name == other.name && this.klass == other.klass && this.desc == other.desc
    }

    override fun replaceUsesOf(ctx: BlockUsageContext, from: UsableBlock, to: UsableBlock) = with(ctx) {
        (0 until innerBlocks.size)
            .filter { basicBlocks[it] == from }
            .forEach {
                innerBlocks[it].removeUser(this@Method)
                innerBlocks[it] = to.get()
                to.addUser(this@Method)
            }
    }

    override val graphView: List<GraphView>
        get() {
            val nodes = hashMapOf<String, GraphView>()
            nodes[name] = GraphView(name, prototype)

            for (bb in basicBlocks) {
                val label = StringBuilder()
                label.append("${bb.name}: ${bb.predecessors.joinToString(", ") { it.name.toString() }}\\l")
                bb.instructions.forEach { label.append("    ${it.print().replace("\"", "\\\"")}\\l") }
                nodes[bb.name.toString()] = GraphView(bb.name.toString(), label.toString())
            }

            if (!isAbstract) {
                val entryNode = nodes.getValue(entry.name.toString())
                nodes.getValue(name).addSuccessor(entryNode)
            }

            for (it in basicBlocks) {
                val current = nodes.getValue(it.name.toString())
                for (successor in it.successors) {
                    current.addSuccessor(nodes.getValue(successor.name.toString()))
                }
            }

            return nodes.values.toList()
        }

    fun view(dot: String, viewer: String) {
        view(name, dot, viewer)
    }
}


val Method.allValues: Set<Value> get() = flatten().flatMap { it.operands + it }.toSet()