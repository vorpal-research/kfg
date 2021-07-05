package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method

sealed class Name {
    internal var index: Int = -1

    abstract fun clone(): Name
    override fun hashCode() = System.identityHashCode(this)
    override fun equals(other: Any?) = this === other
}

class StringName(val name: String) : Name() {
    override fun clone() = StringName(name)
    override fun toString(): String {
        val suffix = if (index == -1) "${hashCode()}" else "$index"
        return "%$name$suffix"
    }
}

class Slot : Name() {
    override fun clone() = Slot()
    override fun toString(): String {
        return if (index == -1) "SLOT_FOR_${hashCode()}" else "%$index"
    }
}

class BlockName(val name: String) : Name() {
    override fun clone() = BlockName(name)
    override fun toString(): String {
        val suffix = if (index == -1) "${hashCode()}" else "$index"
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
        return if (index == -1) "UNDEFINED_${hashCode()}" else "%undefined$index"
    }
}

class SlotTracker(val method: Method) {
    private val blocks = hashMapOf<String, Int>()
    private val strings = hashMapOf<String, Int>()
    private var slots: Int = 0
    private var undefs: Int = 0

    private val nameToValue = hashMapOf<Name, Value>()
    private val nameToBlock = hashMapOf<BlockName, BasicBlock>()

    fun addBlock(block: BasicBlock) {
        val name = block.name
        name.index = blocks.getOrDefault(name.name, 0)
        blocks[name.name] = name.index + 1
        nameToBlock[name] = block
    }

    fun removeBlock(block: BasicBlock) {
        nameToBlock.remove(block.name)
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
        if (name !is ConstantName) nameToValue[name] = value
    }

    fun removeValue(value: Value) {
        nameToValue.remove(value.name)
    }

    fun getBlock(name: String) = nameToBlock
        .filter { it.key.toString() == name }
        .map { it.value }
        .firstOrNull()

    fun getBlock(name: BlockName) = nameToBlock[name]

    fun getValue(name: String) = nameToValue
        .filter { it.key.toString() == name }
        .map { it.value }
        .firstOrNull()

    fun getValue(name: Name) = nameToValue[name]

    internal fun clear() {
        nameToValue.clear()
        nameToBlock.clear()
        strings.clear()
        slots = 0
        blocks.clear()
        undefs = 0
    }

    fun init() {
        method.namesGenerated = true

        clear()
        for (bb in method) {
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
                    if (value !is Constant) nameToValue[name] = value
                }
            }
        }
    }
}
