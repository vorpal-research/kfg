package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.UnexpectedException
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.util.defaultHashCode

sealed class Name {
    internal var st: SlotTracker? = null

    override fun hashCode() = System.identityHashCode(this)
    override fun equals(other: Any?) = this === other
}

class StringName(val name: String) : Name() {
    private fun getNumber() = st?.getStringNumber(this) ?: -1
    override fun toString(): String {
        val number = getNumber()
        val suffix = if (number == -1) "" else "$number"
        return "%$name$suffix"
    }
}

class Slot : Name() {
    private fun getNumber() = st?.getSlotNumber(this) ?: -1
    override fun toString(): String {
        val num = this.getNumber()
        return if (num == -1) "NO_SLOT_FOR${System.identityHashCode(this)}" else "%$num"
    }
}

class BlockName(val name: String) : Name() {
    private fun getNumber() = st?.getBlockNumber(this) ?: -1
    override fun toString(): String {
        val number = getNumber()
        val suffix = if (number == -1) "" else "$number"
        return "%$name$suffix"
    }
}

class ConstantName(val name: String) : Name() {
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
    override fun toString(): String = throw UnexpectedException("Trying to print undefined name")
}

class SlotTracker(val method: Method) {
    private val blocks = mutableMapOf<String, MutableList<BlockName>>()
    private val strings = mutableMapOf<String, MutableList<StringName>>()
    private val slots = mutableMapOf<Slot, Int>()

    fun getBlockNumber(name: BlockName) = blocks[name.name]?.indexOf(name) ?: -1
    fun getStringNumber(name: StringName) = strings[name.name]?.indexOf(name) ?: -1
    fun getSlotNumber(slot: Slot) = slots.getOrDefault(slot, -1)

    fun addBlock(block: BasicBlock) {
        val name = block.name
        name.st = this
        blocks.getOrPut(name.name, { mutableListOf() }).add(name)
    }

    fun addValue(value: Value) {
        val name = value.name
        name.st = this
        when (name) {
            is Slot -> slots[name] = slots.size
            is StringName -> {
                strings.getOrPut(name.name, { mutableListOf() }).add(name)
            }
        }
    }

    fun rerun() {
        strings.clear()
        slots.clear()
        var slotCount = 0
        for (bb in method) {
            val names = blocks.getOrPut(bb.name.name, { mutableListOf() })
            if (!names.contains(bb.name)) names.add(bb.name)
            for (inst in bb) {
                for (value in inst.plus(inst)) {
                    when (value.name) {
                        is Slot -> {
                            slots.getOrPut(value.name, { slotCount++ })
                        }
                        is StringName -> {
                            val names = strings.getOrPut(value.name.name, { mutableListOf() })
                            if (!names.contains(value.name)) names.add(value.name)
                        }
                    }
                }
            }
        }
    }
}
