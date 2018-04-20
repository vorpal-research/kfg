package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.UnexpectedException
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.util.defaultHashCode
import org.jetbrains.research.kfg.util.viewCfg

sealed class Name {
    internal var st: SlotTracker? = null

    abstract fun clone(): Name
    override fun hashCode() = System.identityHashCode(this)
    override fun equals(other: Any?) = this === other
}

class StringName(val name: String) : Name() {
    override fun clone() = StringName(name)
    private fun getNumber() = st?.getStringNumber(this) ?: -1
    override fun toString(): String {
        val number = getNumber()
        val suffix = if (number == -1) "" else "$number"
        return "%$name$suffix"
    }
}

class Slot : Name() {
    override fun clone() = Slot()
    private fun getNumber() = st?.getSlotNumber(this) ?: -1
    override fun toString(): String {
        val num = this.getNumber()
        return if (num == -1) "NO_SLOT_FOR${System.identityHashCode(this)}" else "%$num"
    }
}

class BlockName(val name: String) : Name() {
    override fun clone() = BlockName(name)
    private fun getNumber() = st?.getBlockNumber(this) ?: -1
    override fun toString(): String {
        val number = getNumber()
        val suffix = if (number == -1) "" else "$number"
        return "%$name$suffix"
    }
}

class ConstantName(val name: String) : Name() {
    override fun clone() = ConstantName(name)
    override fun toString() = name
    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (this.javaClass != other?.javaClass) return false
        other as ConstantName
        return this.name == other.name
    }
}

object UndefinedName : Name() {
    override fun clone() = this
    override fun toString(): String = throw UnexpectedException("Trying to print undefined name")
}

class SlotTracker(val method: Method) {
    private val blocks = mutableMapOf<String, MutableList<BlockName>>()
    private val strings = mutableMapOf<String, MutableList<StringName>>()
    private val slots = mutableMapOf<Slot, Int>()

    private val nameToValue = mutableMapOf<Name, Value>()
    private val nameToBlock = mutableMapOf<BlockName, BasicBlock>()

    internal fun getBlockNumber(name: BlockName) = blocks[name.name]?.indexOf(name) ?: -1
    internal fun getStringNumber(name: StringName) = strings[name.name]?.indexOf(name) ?: -1
    internal fun getSlotNumber(slot: Slot) = slots.getOrDefault(slot, -1)

    fun addBlock(block: BasicBlock) {
        val name = block.name
        name.st = this
        blocks.getOrPut(name.name, { mutableListOf() }).add(name)
        nameToBlock[name] = block
    }

    fun addValue(value: Value) {
        val name = value.name
        name.st = this
        when (name) {
            is Slot -> slots[name] = slots.size
            is StringName -> {
                strings.getOrPut(name.name, { mutableListOf() }).add(name)
            }
            else -> {
            }
        }
        if (name !is UndefinedName) nameToValue[name] = value
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

    fun rerun() {
        strings.clear()
        slots.clear()
        blocks.clear()
        var slotCount = 0
        for (bb in method) {
            val names = blocks.getOrPut(bb.name.name, { mutableListOf() })
            if (!names.contains(bb.name)) names.add(bb.name)
            for (inst in bb) {
                for (value in inst.operands().plus(inst)) {
                    when (value.name) {
                        is Slot -> {
                            slots.getOrPut(value.name, { slotCount++ })
                        }
                        is StringName -> {
                            val nameCopies = strings.getOrPut(value.name.name, { mutableListOf() })
                            if (!nameCopies.contains(value.name)) nameCopies.add(value.name)
                        }
                        else -> {

                        }
                    }
                }
            }
        }
    }
}
