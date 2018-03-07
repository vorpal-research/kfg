package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory
import java.rmi.UnexpectedException

sealed class ValueName
class StrName(val value: String) : ValueName() {
    override fun toString() = value
}

class Slot(private val st: SlotTracker) : ValueName() {
    fun getNumber() = st.getSlotNumber(this)
    override fun toString(): String {
        val num = this.getNumber()
        return if (num == -1) "NO_SLOT_FOR${System.identityHashCode(this)}" else "%$num"
    }
}

class UndefinedName private constructor() : ValueName() {
    companion object {
        val instance = UndefinedName()
    }

    override fun toString(): String = throw UnexpectedException("Trying to print undefined name")
}

class SlotTracker(val method: Method) {
    private val slots = mutableMapOf<Slot, Int>()

    fun getSlotNumber(slot: Slot) = slots.getOrDefault(slot, -1)

    fun getNextSlot(): Slot {
        val slot = Slot(this)
        slots[slot] = slots.size
        return slot
    }

    fun rerun() {
        slots.clear()
        var count = 0
        for (inst in method.flatten()) {
            for (value in inst)
                if (value.name is Slot) slots.getOrPut(value.name, { count++ })
            if (inst.name is Slot) slots.getOrPut(inst.name, { count++ })
        }
    }
}

abstract class Value(val name: ValueName, val type: Type) : Usable<Value> {
    private val users = mutableSetOf<User<Value>>()

    constructor(name: String, type: Type) : this(StrName(name), type)

    fun isNameDefined() = name is UndefinedName
    fun hasRealName() = name is StrName
    override fun toString() = name.toString()

    override fun addUser(user: User<Value>) {
        users.add(user)
    }

    override fun removeUser(user: User<Value>) {
        users.remove(user)
    }

    override fun getUsers(): List<User<Value>> = users.toList()

    override fun replaceAllUsesWith(to: Value) {
        getUsers().forEach { it.replaceUsesOf(this, to) }
    }
}

class Argument(argName: String, val method: Method, type: Type) : Value(argName, type)
class ThisRef(type: Type) : Value("this", type)