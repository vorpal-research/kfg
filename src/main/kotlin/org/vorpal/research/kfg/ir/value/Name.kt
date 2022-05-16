package org.vorpal.research.kfg.ir.value

import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.MethodBody

sealed class Name {
    internal var index: Int = -1

    abstract fun clone(): Name
    override fun hashCode() = System.identityHashCode(this)
    override fun equals(other: Any?) = this === other
}

class StringName(val name: String) : Name() {
    override fun clone() = StringName(name)
    override fun toString(): String {
        val suffix = if (index == -1) "" else "$index"
        return "%$name$suffix"
    }
}

class Slot : Name() {
    override fun clone() = Slot()
    override fun toString(): String {
        return if (index == -1) "NO_SLOT_FOR${System.identityHashCode(this)}" else "%$index"
    }
}

class BlockName(val name: String) : Name() {
    override fun clone() = BlockName(name)
    override fun toString(): String {
        val suffix = if (index == -1) "" else "$index"
        return "%$name$suffix"
    }
}

data class ConstantName(val name: String) : Name() {
    override fun clone() = ConstantName(name)
    override fun toString() = name
}

class UndefinedName() : Name() {
    override fun clone() = this
    override fun toString(): String {
        return if (index == -1) "UNDEFINED_${System.identityHashCode(this)}" else "undefined$index"
    }
}

class SlotTracker(private val methodBody: MethodBody) {
    private val blocks = hashMapOf<String, Int>()
    private val strings = hashMapOf<String, Int>()
    private var slots: Int = 0
    private var undefs: Int = 0

    constructor(method: Method) : this(method.body)

    fun addBlock(block: BasicBlock) {
        val name = block.name
        name.index = blocks.getOrDefault(name.name, 0)
        blocks[name.name] = name.index + 1
    }

    @Suppress("UNUSED_PARAMETER")
    fun removeBlock(block: BasicBlock) {
        // nothing
    }

    fun addValue(value: Value) {
        val name = value.name
        name.index = when (name) {
            is Slot -> slots++
            is StringName -> {
                val result = strings.getOrDefault(name.name, 0)
                strings[name.name] = result + 1
                result
            }
            is UndefinedName -> undefs++
            else -> -1
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun removeValue(value: Value) {
        // nothing
    }

    fun rerun() {
        strings.clear()
        slots = 0
        blocks.clear()
        undefs = 0
        for (bb in methodBody) {
            addBlock(bb)
            for (inst in bb) {
                for (value in inst.operands.plus(inst.get())) {
                    val name = value.name
                    name.index = when (name) {
                        is Slot -> slots++
                        is UndefinedName -> undefs++
                        is StringName -> {
                            val result = strings.getOrDefault(name.name, 0)
                            strings[name.name] = result + 1
                            result
                        }
                        else -> -1
                    }
                }
            }
        }
    }
}

class NameMapper(val method: Method) {
    private val stringToName = hashMapOf<String, Name>()
    private val nameToValue = hashMapOf<Name, Value>()
    private val nameToBlock = hashMapOf<BlockName, BasicBlock>()

    init {
        init()
    }

    fun getBlock(name: String) = nameToBlock
        .filter { it.key.toString() == name }
        .map { it.value }
        .firstOrNull()

    fun getBlock(name: BlockName) = nameToBlock[name]

    fun getValue(name: String) = nameToValue[stringToName[name]]

    fun getValue(name: Name) = nameToValue[name]

    private fun init() {
        for (bb in method.body) {
            nameToBlock[bb.name] = bb
            for (inst in bb) {
                for (value in inst.operands.plus(inst.get())) {
                    val name = value.name
                    if (value !is Constant) {
                        nameToValue[name] = value
                        stringToName[name.toString()] = name
                    }
                }
            }
        }
    }
}

class NameMapperContext {
    private val mappers = mutableMapOf<Method, NameMapper>()

    fun getMapper(method: Method) = mappers.getOrPut(method) { NameMapper(method) }

    fun clear() {
        mappers.clear()
    }
}

val Method.nameMapper get() = NameMapper(this)
