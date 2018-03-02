package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.ir.value.SlotTracker
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.parseMethodDesc

fun createMethodDesc(name: String, `class`: Class, args: Array<Type>, retType: Type): String {
    val sb = StringBuilder()
    sb.append("${retType.getName()} ${`class`.name}::$name(")
    args.dropLast(1).forEach { sb.append("${it.getName()}, ") }
    args.takeLast(1).forEach { sb.append(it.getName()) }
    sb.append(")")
    return sb.toString()
}

class Method : Node, Iterable<BasicBlock> {
    val `class`: Class
    val argTypes: Array<Type>
    val parameters = mutableListOf<Parameter>()
    val retType: Type
    val basicBlocks = mutableListOf<BasicBlock>()
    val catchBlocks = mutableListOf<BasicBlock>()
    val slottracker = SlotTracker(this)

    constructor(name: String, classRef: Class, desc: String) : super(name) {
        this.`class` = classRef
        val pr = parseMethodDesc(desc)
        this.argTypes = pr.first
        this.retType = pr.second
    }

    constructor(name: String, classRef: Class, modifiers: Int, desc: String) : super(name, modifiers) {
        this.`class` = classRef
        val pr = parseMethodDesc(desc)
        this.argTypes = pr.first
        this.retType = pr.second
    }

    constructor(name: String, classRef: Class, modifiers: Int, arguments: Array<Type>, retType: Type)
            : super(name, modifiers) {
        this.`class` = classRef
        this.argTypes = arguments
        this.retType = retType
    }

    fun getEntry() = basicBlocks.first()
    fun addBasicBlock(bb: BasicBlock) = basicBlocks.add(bb)

    fun addIfNotContains(bb: BasicBlock) {
        if (!basicBlocks.contains(bb)) basicBlocks.add(bb)
    }

    fun addCatchBlock(bb: BasicBlock) = catchBlocks.add(bb)

    fun getBlockRange(from: BasicBlock, to: BasicBlock): List<BasicBlock> {
        val start = basicBlocks.indexOf(from)
        val end = basicBlocks.indexOf(to)
        return basicBlocks.subList(start, end)
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

    override fun toString() = getDesc()
    override fun iterator() = basicBlocks.iterator()
}