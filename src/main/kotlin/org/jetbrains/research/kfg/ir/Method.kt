package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.parseMethodDesc
import org.objectweb.asm.Opcodes

class Method{
    val name: String
    val classRef: Class
    val modifiers: Int
    val arguments: Array<Type>
    val retType: Type
    val basicBlocks = mutableListOf<BasicBlock>()

    constructor(name: String, classRef: Class, modifiers: Int, arguments: Array<Type>, retType: Type) {
        this.name = name
        this.classRef = classRef
        this.modifiers = modifiers
        this.arguments = arguments
        this.retType = retType
    }

    constructor(name: String, classRef: Class, modifiers: Int, desc: String) {
        this.name = name
        this.classRef = classRef
        this.modifiers = modifiers
        val pr = parseMethodDesc(desc)
        this.arguments = pr.first
        this.retType = pr.second
    }

    fun addBasicBlock(bb: BasicBlock) = basicBlocks.add(bb)
    fun addIfNotContains(bb: BasicBlock) {
        if (!basicBlocks.contains(bb)) basicBlocks.add(bb)
    }

    fun getBlockRange(from: BasicBlock, to: BasicBlock): List<BasicBlock> {
        val start = basicBlocks.indexOf(from)
        val end = basicBlocks.indexOf(to)
        return basicBlocks.subList(start, end)
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("${retType.getName()} ${classRef.name}::$name(")
        arguments.dropLast(1).forEach { sb.append("${it.getName()}, ") }
        arguments.takeLast(1).forEach { sb.append(it.getName()) }
        sb.append(")")
        return sb.toString()
    }

    fun print(): String {
        val sb = StringBuilder()
        sb.appendln(toString())
        basicBlocks.forEach { sb.appendln(it) }
        return sb.toString()
    }

    fun isPublic() = isPublic(modifiers)
    fun isPrivate() = isPrivate(modifiers)
    fun isProtected() = isProtected(modifiers)
    fun isStatic()= isStatic(modifiers)
    fun isFinal() = isFinal(modifiers)
    fun isSynchronized() = isSynchronized(modifiers)
    fun isBridge() = isBridge(modifiers)
    fun isVarArg() = isVarargs(modifiers)
    fun isNative() = isNative(modifiers)
    fun isAbstract() = isAbstract(modifiers)
    fun isStrict() = isStrict(modifiers)
    fun isSynthetic() = isSynthetic(modifiers)
}