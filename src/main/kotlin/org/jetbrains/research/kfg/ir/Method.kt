package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.value.BlockUser
import org.jetbrains.research.kfg.ir.value.SlotTracker
import org.jetbrains.research.kfg.ir.value.UsableBlock
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.type.parseMethodDesc
import org.jetbrains.research.kfg.util.GraphView
import org.jetbrains.research.kfg.util.simpleHash
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.ParameterNode
import java.util.*

data class MethodDesc(val args: Array<Type>, val retval: Type) {

    companion object {
        fun fromDesc(tf: TypeFactory, desc: String): MethodDesc {
            val (args, retval) = parseMethodDesc(tf, desc)
            return MethodDesc(args, retval)
        }
    }

    val asmDesc: String
        get() {
            val sb = StringBuilder()
            sb.append("(")
            args.forEach { type -> sb.append(type.asmDesc) }
            sb.append(")")
            sb.append(retval.asmDesc)
            return sb.toString()
        }

    override fun hashCode() = simpleHash(*args, retval)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as MethodDesc
        return this.args.contentEquals(other.args) && this.retval == other.retval
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("(")
        sb.append(args.joinToString { it.name })
        sb.append("): ${retval.name}")
        return sb.toString()
    }
}

class Method(cm: ClassManager, val mn: MethodNode, val `class`: Class) : Node(cm, mn.name, mn.access), Iterable<BasicBlock>, BlockUser {

    companion object {
        private val CONSTRUCTOR_NAMES = arrayOf("<init>", "<clinit>")
    }

    val desc = MethodDesc.fromDesc(cm.type, mn.desc)
    val argTypes get() = desc.args
    val returnType get() = desc.retval
    val parameters = arrayListOf<Parameter>()
    val exceptions = hashSetOf<Class>()
    val basicBlocks = arrayListOf<BasicBlock>()
    val catchEntries = hashSetOf<CatchBlock>()
    val slottracker = SlotTracker(this)

    init {
        mn.parameters?.withIndex()?.forEach { (indx, param) ->
            param as ParameterNode
            parameters.add(Parameter(cm, indx, param.name, desc.args[indx], param.access))
        }

        mn.exceptions?.forEach { exceptions.add(cm.getByName(it as String)) }

        addVisibleAnnotations(@Suppress("UNCHECKED_CAST") (mn.visibleAnnotations as List<AnnotationNode>?))
        addInvisibleAnnotations(@Suppress("UNCHECKED_CAST") (mn.invisibleAnnotations as List<AnnotationNode>?))
    }

    val entry: BasicBlock
        get() = basicBlocks.first()

    val prototype: String
        get() = "$`class`.$name$desc"

    val isConstructor: Boolean
        get() = name in CONSTRUCTOR_NAMES

    val bodyBlocks: List<BasicBlock>
        get() {
            val catches = catchBlocks
            return basicBlocks.filter { it !in catches }.toList()
        }

    val catchBlocks: List<BasicBlock>
        get() {
            val catchMap = hashMapOf<BasicBlock, Boolean>()
            val result = arrayListOf<BasicBlock>()
            val queue = ArrayDeque<BasicBlock>()
            queue.addAll(catchEntries)
            while (queue.isNotEmpty()) {
                val top = queue.first
                val isCatch = top.predecessors.fold(true) { acc, bb -> acc && catchMap.getOrPut(bb) { false } }
                if (isCatch) {
                    result.add(top)
                    queue.addAll(top.successors)
                    catchMap[top] = true
                }
                queue.pollFirst()
            }
            return result
        }

    override val asmDesc
        get() = desc.asmDesc

    fun isEmpty() = basicBlocks.isEmpty()
    fun isNotEmpty() = !isEmpty()

    fun add(bb: BasicBlock) {
        if (!basicBlocks.contains(bb)) {
            require(bb.parent == null) { "Block ${bb.name} already belongs to other method" }
            basicBlocks.add(bb)
            slottracker.addBlock(bb)
            bb.addUser(this)
            bb.parent = this
        }
    }

    fun addBefore(before: BasicBlock, bb: BasicBlock) {
        if (!basicBlocks.contains(bb)) {
            require(bb.parent == null) { "Block ${bb.name} already belongs to other method" }
            val index = basicBlocks.indexOf(before)
            require(index >= 0) { "Block ${before.name} does not belong to method $this" }

            basicBlocks.add(index, bb)
            slottracker.addBlock(bb)
            bb.addUser(this)
            bb.parent = this
        }
    }

    fun remove(block: BasicBlock) {
        if (basicBlocks.contains(block)) {
            require(block.parent == this) { "Block ${block.name} don't belong to $this" }
            basicBlocks.remove(block)

            if (block in catchEntries) {
                catchEntries.remove(block)
            }

            block.removeUser(this)
            block.parent = null
        }
    }

    fun addCatchBlock(bb: CatchBlock) {
        require(bb in basicBlocks)
        catchEntries.add(bb)
    }

    fun getNext(from: BasicBlock): BasicBlock {
        val start = basicBlocks.indexOf(from)
        return basicBlocks[start + 1]
    }

    fun getBlockByLocation(location: Location) = basicBlocks.find { it.location == location }
    fun getBlockByName(name: String) = basicBlocks.find { it.name.toString() == name }

    fun print() = buildString {
        appendln(prototype)
        append(basicBlocks.joinToString(separator = "\n\n") { "$it" })
    }

    override fun toString() = prototype
    override fun iterator() = basicBlocks.iterator()

    override fun hashCode() = simpleHash(name, `class`, desc)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Method
        return this.name == other.name && this.`class` == other.`class` && this.desc == other.desc
    }

    override fun replaceUsesOf(from: UsableBlock, to: UsableBlock) {
        (0 until basicBlocks.size)
                .filter { basicBlocks[it] == from }
                .forEach {
                    basicBlocks[it].removeUser(this)
                    basicBlocks[it] = to.get()
                    to.addUser(this)
                }
    }

    fun graphView(viewCatchBlocks: Boolean = false): List<GraphView> {
        val nodes = hashMapOf<String, GraphView>()
        nodes[name] = GraphView(name, prototype)

        basicBlocks.map { bb ->
            val label = StringBuilder()
            label.append("${bb.name}: ${bb.predecessors.joinToString(", ") { it.name.toString() }}\\l")
            bb.instructions.forEach { label.append("    ${it.print().replace("\"", "\\\"")}\\l") }
            nodes[bb.name.toString()] = GraphView(bb.name.toString(), label.toString())
        }

        if (!isAbstract) {
            val entryNode = nodes.getValue(entry.name.toString())
            nodes.getValue(name).successors.add(entryNode)
        }

        basicBlocks.forEach {
            val current = nodes.getValue(it.name.toString())
            for (succ in it.successors) {
                current.successors.add(nodes.getValue(succ.name.toString()))
            }
        }

        if (viewCatchBlocks) {
            catchEntries.forEach {
                val current = nodes.getOrPut(it.name.toString()) { GraphView(it.name.toString(), "${it.name}:\\l") }
                for (thrower in it.throwers) {
                    nodes.getValue(thrower.name.toString()).successors.add(current)
                }
            }
        }
        return nodes.values.toList()
    }

    fun viewCfg(dot: String, browser: String, viewCatchBlocks: Boolean = false) =
            org.jetbrains.research.kfg.util.viewCfg(name, graphView(viewCatchBlocks), dot, browser)
}