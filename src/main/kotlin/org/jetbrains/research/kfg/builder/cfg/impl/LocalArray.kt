package org.jetbrains.research.kfg.builder.cfg.impl

import org.jetbrains.research.kfg.ir.value.UsableValue
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.ValueUser

internal class LocalArray(private val locals: MutableMap<Int, Value> = hashMapOf())
    : ValueUser, MutableMap<Int, Value> by locals {
    override fun clear() {
        values.forEach { it.removeUser(this) }
        locals.clear()
    }

    override fun put(key: Int, value: Value): Value? {
        value.addUser(this)
        val prev = locals.put(key, value)
        prev?.removeUser(this)
        return prev
    }

    override fun putAll(from: Map<out Int, Value>) {
        from.forEach {
            put(it.key, it.value)
        }
    }

    override fun remove(key: Int): Value? {
        val res = locals.remove(key)
        res?.removeUser(this)
        return res
    }

    override fun replaceUsesOf(from: UsableValue, to: UsableValue) {
        entries.forEach { (key, value) ->
            if (value == from) {
                value.removeUser(this)
                locals[key] = to.get()
                to.addUser(this)
            }
        }
    }
}