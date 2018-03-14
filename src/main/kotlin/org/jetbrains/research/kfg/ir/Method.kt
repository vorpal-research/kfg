package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.builder.cfg.CfgBuilder
import org.jetbrains.research.kfg.defaultHasCode
import org.jetbrains.research.kfg.ir.value.SlotTracker
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.parseMethodDesc
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.ParameterNode
import org.objectweb.asm.tree.TypeAnnotationNode

fun createMethodDesc(name: String, `class`: Class, args: Array<Type>, retType: Type): String {
    val sb = StringBuilder()
    sb.append("${retType.name} ${`class`.name}::$name(")
    args.dropLast(1).forEach { sb.append("${it.name}, ") }
    args.takeLast(1).forEach { sb.append(it.name) }
    sb.append(")")
    return sb.toString()
}

class Method(val mn: MethodNode, val `class`: Class) : Node(mn.name, mn.access), Iterable<BasicBlock> {
    val argTypes: Array<Type>
    val parameters = mutableListOf<Parameter>()
    val retType: Type
    val exceptions = mutableSetOf<Class>()
    val basicBlocks = mutableListOf<BasicBlock>()
    val catchBlocks = mutableListOf<BasicBlock>()
    val slottracker = SlotTracker(this)

    init {
        val pr = parseMethodDesc(mn.desc)
        argTypes = pr.first
        retType = pr.second

        if (mn.parameters != null) {
            mn.parameters.withIndex().forEach { (indx, param) ->
                param as ParameterNode
                parameters.add(Parameter(indx, param.name, argTypes[indx], param.access))
            }
        }

        if (mn.exceptions != null) {
            mn.exceptions.forEach { exceptions.add(CM.getByName(it as String)) }
        }

        addVisibleAnnotations(mn.visibleAnnotations as List<AnnotationNode>?)
        addInvisibleAnnotations(mn.invisibleAnnotations as List<AnnotationNode>?)
    }

    fun getEntry() = basicBlocks.first()

    fun addIfNotContains(bb: BasicBlock) {
        if (!basicBlocks.contains(bb)) basicBlocks.add(bb)
    }

    fun addCatchBlock(bb: BasicBlock) = catchBlocks.add(bb)

    fun getBlockRange(from: BasicBlock, to: BasicBlock): List<BasicBlock> {
        val start = basicBlocks.indexOf(from)
        val end = basicBlocks.indexOf(to)
        return basicBlocks.subList(start, end)
    }

    fun getNext(from: BasicBlock): BasicBlock {
        val start = basicBlocks.indexOf(from)
        return basicBlocks[start + 1]
    }

    fun getDesc() = createMethodDesc(name, `class`, argTypes, retType)

    fun print(): String {
        val sb = StringBuilder()
        sb.appendln(getDesc())
        basicBlocks.take(1).forEach { sb.appendln(it) }
        basicBlocks.drop(1).dropLast(1).forEach { sb.appendln("\n$it") }
        basicBlocks.drop(1).takeLast(1).forEach { sb.append("\n$it") }
        return sb.toString()
    }

    override fun getAsmDesc(): String {
        val sb = StringBuilder()
        sb.append("(")
        argTypes.forEach { type -> sb.append(type.getAsmDesc()) }
        sb.append(")")
        sb.append(retType.getAsmDesc())
        return sb.toString()
    }

    override fun toString() = getDesc()
    override fun iterator() = basicBlocks.iterator()

    override fun hashCode() = defaultHasCode(name, `class`, argTypes, retType)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Method
        return this.name == other.name && this.`class` == other.`class`
                && this.argTypes.contentEquals(other.argTypes) && this.retType == other.argTypes
    }
}