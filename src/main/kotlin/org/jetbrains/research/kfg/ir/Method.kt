package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.util.defaultHashCode
import org.jetbrains.research.kfg.ir.value.SlotTracker
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.parseMethodDesc
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.ParameterNode
import java.util.*

class MethodDesc {
    val args: Array<Type>
    val retval: Type

    constructor(desc: String) {
        val (args, retval) = parseMethodDesc(desc)
        this.args = args
        this.retval = retval
    }

    constructor(args: Array<Type>, retval: Type) {
        this.args = args
        this.retval = retval
    }

    fun getAsmDesc(): String {
        val sb = StringBuilder()
        sb.append("(")
        args.forEach { type -> sb.append(type.getAsmDesc()) }
        sb.append(")")
        sb.append(retval.getAsmDesc())
        return sb.toString()
    }

    override fun hashCode() = defaultHashCode(*args, retval)
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

class Method(val mn: MethodNode, val `class`: Class) : Node(mn.name, mn.access), Iterable<BasicBlock> {
    val desc = MethodDesc(mn.desc)
    val parameters = mutableListOf<Parameter>()
    val exceptions = mutableSetOf<Class>()
    val basicBlocks = mutableListOf<BasicBlock>()
    val catchEntries = mutableSetOf<BasicBlock>()
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
            basicBlocks.add(bb)
            slottracker.addBlock(bb)
        }
    }

    fun addCatchBlock(bb: BasicBlock) {
        catchEntries.add(bb)
    }

    fun getBodyBlocks(): List<BasicBlock> {
        val catches = getCatchBlocks()
        return basicBlocks.filter { it !in catches }.toList()
    }

    fun getCatchBlocks(): List<BasicBlock> {
        val catchMap = mutableMapOf<BasicBlock, Boolean>()
        val result = mutableListOf<BasicBlock>()
        val queue = ArrayDeque<BasicBlock>()
        queue.addAll(catchEntries)
        while (queue.isNotEmpty()) {
            val top = queue.first
            val isCatch = top.predecessors.fold(true, { acc, bb -> acc and catchMap.getOrPut(bb, { false })})
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

    fun getPrototype() = "$`class`.$name$desc"

    fun print(): String {
        val sb = StringBuilder()
        sb.appendln(desc)
        basicBlocks.take(1).forEach { sb.appendln(it) }
        basicBlocks.drop(1).dropLast(1).forEach { sb.appendln("\n$it") }
        basicBlocks.drop(1).takeLast(1).forEach { sb.append("\n$it") }
        return sb.toString()
    }

    override fun getAsmDesc() = desc.getAsmDesc()

    override fun toString() = getPrototype()
    override fun iterator() = basicBlocks.iterator()

    override fun hashCode() = defaultHashCode(name, `class`, desc)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Method
        return this.name == other.name && this.`class` == other.`class` && this.desc == other.desc
    }
}