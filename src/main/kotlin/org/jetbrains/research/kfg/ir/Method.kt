package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.ir.value.BlockUser
import org.jetbrains.research.kfg.ir.value.SlotTracker
import org.jetbrains.research.kfg.ir.value.UsableBlock
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.parseMethodDesc
import org.jetbrains.research.kfg.util.GraphView
import org.jetbrains.research.kfg.util.simpleHash
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.ParameterNode
import java.util.*

data class MethodDesc(val args: Array<Type>, val retval: Type) {

    companion object {
        fun fromDesc(desc: String): MethodDesc {
            val (args, retval) = parseMethodDesc(desc)
            return MethodDesc(args, retval)
        }
    }

    fun getAsmDesc(): String {
        val sb = StringBuilder()
        sb.append("(")
        args.forEach { type -> sb.append(type.getAsmDesc()) }
        sb.append(")")
        sb.append(retval.getAsmDesc())
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
        args.dropLast(1).forEach { sb.append("${it.name}, ") }
        args.takeLast(1).forEach { sb.append(it.name) }
        sb.append("): ${retval.name}")
        return sb.toString()
    }
}

class Method(val mn: MethodNode, val `class`: Class) : Node(mn.name, mn.access), Iterable<BasicBlock>, BlockUser {
    val desc = MethodDesc.fromDesc(mn.desc)
    val parameters = arrayListOf<Parameter>()
    val exceptions = hashSetOf<Class>()
    val basicBlocks = arrayListOf<BasicBlock>()
    val catchEntries = hashSetOf<CatchBlock>()
    val slottracker = SlotTracker(this)

    init {
        if (mn.parameters != null) {
            mn.parameters.withIndex().forEach { (indx, param) ->
                param as ParameterNode
                parameters.add(Parameter(indx, param.name, desc.args[indx], param.access))
            }
        }

        if (mn.exceptions != null) {
            mn.exceptions.forEach { exceptions.add(CM.getByName(it as String)) }
        }

        addVisibleAnnotations(@Suppress("UNCHECKED_CAST") (mn.visibleAnnotations as List<AnnotationNode>?))
        addInvisibleAnnotations(@Suppress("UNCHECKED_CAST") (mn.invisibleAnnotations as List<AnnotationNode>?))
    }

    fun getEntry() = basicBlocks.first()

    fun add(bb: BasicBlock) {
        if (!basicBlocks.contains(bb)) {
            require(bb.parent == null) { "Block ${bb.name} already belongs to other method"}
            basicBlocks.add(bb)
            slottracker.addBlock(bb)
            bb.addUser(this)
            bb.parent = this
        }
    }

    fun addBefore(before: BasicBlock, bb: BasicBlock) {
        if (!basicBlocks.contains(bb)) {
            require(bb.parent == null) { "Block ${bb.name} already belongs to other method"}
            val index = basicBlocks.indexOf(before)
            require(index >= 0) { "Block ${before.name} does not belong to method $this"}

            basicBlocks.add(index, bb)
            slottracker.addBlock(bb)
            bb.addUser(this)
            bb.parent = this
        }
    }

    fun remove(block: BasicBlock) {
        if (basicBlocks.contains(block)) {
            require(block.parent == this) { "Block ${block.name} don't belong to $this"}
            basicBlocks.remove(block)
            block.removeUser(this)
            block.parent = null
        }
    }

    fun addCatchBlock(bb: CatchBlock) {
        require(bb in basicBlocks)
        catchEntries.add(bb)
    }

    fun getBodyBlocks(): List<BasicBlock> {
        val catches = getCatchBlocks()
        return basicBlocks.filter { it !in catches }.toList()
    }

    fun getCatchBlocks(): List<BasicBlock> {
        val catchMap = hashMapOf<BasicBlock, Boolean>()
        val result = arrayListOf<BasicBlock>()
        val queue = ArrayDeque<BasicBlock>()
        queue.addAll(catchEntries)
        while (queue.isNotEmpty()) {
            val top = queue.first
            val isCatch = top.predecessors.fold(true) { acc, bb -> acc and catchMap.getOrPut(bb) { false } }
            if (isCatch) {
                queue.addAll(top.successors)
                catchMap[top] = true
            }
            queue.pollFirst()
        }
        return result
    }

    fun getNext(from: BasicBlock): BasicBlock {
        val start = basicBlocks.indexOf(from)
        return basicBlocks[start + 1]
    }

    fun getBlockByLocation(location: Location) = basicBlocks.find { it.location == location }

    fun getPrototype() = "$`class`.$name$desc"

    fun print(): String {
        val sb = StringBuilder()
        sb.appendln(getPrototype())
        basicBlocks.take(1).forEach { sb.appendln(it) }
        basicBlocks.drop(1).dropLast(1).forEach { sb.appendln("\n$it") }
        basicBlocks.drop(1).takeLast(1).forEach { sb.append("\n$it") }
        return sb.toString()
    }

    override fun getAsmDesc() = desc.getAsmDesc()

    override fun toString() = getPrototype()
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
}

fun Method.graphView(viewCatchBlocks: Boolean = false): List<GraphView> {
    val nodes = hashMapOf<String, GraphView>()
    nodes[name] = GraphView(name, name)
    basicBlocks.map {
        val label = StringBuilder()
        label.append("${it.name}:\\l")
        it.instructions.forEach { label.append("    ${it.print().replace("\"", "\\\"")}\\l") }
        nodes[it.name.toString()] = GraphView(it.name.toString(), label.toString())
    }
    if (!isAbstract()) {
        val entryNode = nodes.getValue(getEntry().name.toString())
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
            val current = nodes.getValue(it.name.toString())
            for (thrower in it.throwers) {
                current.successors.add(nodes.getValue(thrower.name.toString()))
            }
        }
    }
    return nodes.values.toList()
}

fun Method.viewCfg(viewCatchBlocks: Boolean = false, dot: String, browser: String)
        = org.jetbrains.research.kfg.util.viewCfg(name, graphView(viewCatchBlocks), dot, browser)