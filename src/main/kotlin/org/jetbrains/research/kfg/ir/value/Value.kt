package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.UnexpectedException
import org.jetbrains.research.kfg.util.defaultHashCode
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.Type

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
    private val strings = mutableMapOf<String, MutableList<StringName>>()
    private val slots = mutableMapOf<Slot, Int>()

    fun getStringNumber(name: StringName) = strings[name.name]?.indexOf(name) ?: -1
    fun getSlotNumber(slot: Slot) = slots.getOrDefault(slot, -1)

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
        for (inst in method.flatten()) {
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

abstract class Value(val name: Name, val type: Type) : UsableValue {
    override val users = mutableSetOf<User>()

    fun isNameDefined() = name is UndefinedName
    fun hasRealName() = name is StringName
    override fun toString() = name.toString()

    override fun get() = this
}

class Argument(argName: String, val method: Method, type: Type) : Value(ConstantName(argName), type) {
    override fun hashCode() = defaultHashCode(name, type, method)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as Argument
        return this.name == other.name && this.type == other.type && this.method == other.method
    }
}

class ThisRef(type: Type) : Value(ConstantName("this"), type) {
    override fun hashCode() = defaultHashCode(name, type)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as ThisRef
        return this.type == other.type
    }
}