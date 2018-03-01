package org.jetbrains.research.kfg.builder

import org.jetbrains.research.kfg.ir.value.User
import org.jetbrains.research.kfg.ir.value.Value

class LocalArray : User<Value>, MutableMap<Int, Value> {
    private val locals = mutableMapOf<Int, Value>()

    override val entries: MutableSet<MutableMap.MutableEntry<Int, Value>>
        get() = locals.entries
    override val keys: MutableSet<Int>
        get() = locals.keys
    override val size: Int
        get() = locals.size
    override val values: MutableCollection<Value>
        get() = locals.values

    override fun clear() {
        values.forEach { it.removeUser(this) }
        locals.clear()
    }

    override fun put(key: Int, value: Value): Value? {
        value.addUser(this)
        val prev = locals.put(key, value)
        if (prev != null) prev.removeUser(this)
        return prev
    }

    override fun putAll(from: Map<out Int, Value>) {
        from.forEach {
            put(it.key, it.value)
        }
    }

    override fun remove(key: Int): Value? {
        val res = locals.remove(key)
        if (res != null) res.removeUser(this)
        return res
    }

    override fun containsKey(key: Int) = locals.containsKey(key)
    override fun containsValue(value: Value) = locals.containsValue(value)
    override fun get(key: Int) = locals.get(key)
    override fun isEmpty() = locals.isEmpty()

    override fun replaceUsesOf(from: Value, to: Value) {
        entries.forEach {(key, value) ->
            if (value == from) {
                value.removeUser(this)
                locals[key] = to
                to.addUser(this)
            }
        }
    }
}