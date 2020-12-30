package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.InvalidStateError
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method

sealed class Name {
    internal var st: SlotTracker? = null

    abstract fun clone(): Name
    override fun hashCode() = System.identityHashCode(this)
    override fun equals(other: Any?) = this === other
}

class StringName(val name: String) : Name() {
    private val number get() = st?.getStringNumber(this) ?: -1

    override fun clone() = StringName(name)
    override fun toString(): String {
        val number = this.number
        val suffix = if (number == -1) "" else "$number"
        return "%$name$suffix"
    }
}

class Slot : Name() {
    private val number get() = st?.getSlotNumber(this) ?: -1

    override fun clone() = Slot()
    override fun toString(): String {
        val num = this.number
        return if (num == -1) "NO_SLOT_FOR${System.identityHashCode(this)}" else "%$num"
    }
}

class BlockName(val name: String) : Name() {
    private val number get() = st?.getBlockNumber(this) ?: -1
    override fun clone() = BlockName(name)
    override fun toString(): String {
        val number = number
        val suffix = if (number == -1) "" else "$number"
        return "%$name$suffix"
    }
}

data class ConstantName(val name: String) : Name() {
    override fun clone() = ConstantName(name)
    override fun toString() = name
}

object UndefinedName : Name() {
    override fun clone() = this
    override fun toString(): String = throw InvalidStateError("Trying to print undefined name")
}

class SlotTracker(val method: Method) {
    private val blocks = hashMapOf<String, MutableList<BlockName>>()
    private val strings = hashMapOf<String, MutableList<StringName>>()
    private val slots = hashMapOf<Slot, Int>()

    private val nameToValue = hashMapOf<Name, Value>()
    private val nameToBlock = hashMapOf<BlockName, BasicBlock>()

    internal fun getBlockNumber(name: BlockName) = blocks[name.name]?.indexOf(name) ?: -1
    internal fun getStringNumber(name: StringName) = strings[name.name]?.indexOf(name) ?: -1
    internal fun getSlotNumber(slot: Slot) = slots.getOrDefault(slot, -1)

    fun addBlock(block: BasicBlock) {
        val name = block.name
        name.st = this
        blocks.getOrPut(name.name, ::arrayListOf).add(name)
        nameToBlock[name] = block
    }

    fun addValue(value: Value) {
        val name = value.name
        name.st = this
        when (name) {
            is Slot -> slots[name] = slots.size
            is StringName -> {
                strings.getOrPut(name.name, ::arrayListOf).add(name)
            }
            else -> {
            }
        }
        if (name !== UndefinedName) nameToValue[name] = value
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

    internal fun rerun() {
        strings.clear()
        slots.clear()
        blocks.clear()
        var slotCount = 0
        for (bb in method) {
            val names = blocks.getOrPut(bb.name.name, ::arrayListOf)
            if (!names.contains(bb.name)) names.add(bb.name)
            for (inst in bb) {
                for (value in inst.operands.plus(inst.get())) {
                    when (value.name) {
                        is Slot ->  slots.getOrPut(value.name) { slotCount++ }
                        is StringName -> {
                            val nameCopies = strings.getOrPut(value.name.name, ::arrayListOf)
                            if (!nameCopies.contains(value.name)) nameCopies.add(value.name)
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}
