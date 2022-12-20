package org.vorpal.research.kfg.builder.cfg.impl

import org.vorpal.research.kfg.ir.value.*

internal class LocalArray(
    private val ctx: UsageContext,
    private val locals: MutableMap<Int, Value> = hashMapOf()
) : ValueUser, MutableMap<Int, Value> by locals, UsageContext by ctx {
    private val valueMapping = hashMapOf<Value, MutableSet<Int>>()
    override fun clear() {
        values.forEach { it.removeUser(this) }
        locals.clear()
    }

    override fun put(key: Int, value: Value): Value? {
        value.addUser(this)
        val prev = locals.put(key, value)
        prev?.let {
            it.removeUser(this)
            valueMapping.getOrPut(it, ::mutableSetOf).remove(key)
        }
        valueMapping.getOrPut(value, ::mutableSetOf).add(key)
        return prev
    }

    override fun putAll(from: Map<out Int, Value>) {
        for ((key, value) in from) {
            put(key, value)
        }
    }

    override fun remove(key: Int): Value? {
        val res = locals.remove(key)
        res?.let {
            it.removeUser(this)
            valueMapping.getOrPut(it, ::mutableSetOf).remove(key)
        }
        return res
    }

    override fun replaceUsesOf(ctx: ValueUsageContext, from: UsableValue, to: UsableValue) {
        val fromKeys = valueMapping.getOrPut(from.get(), ::mutableSetOf)
        val toKeys = valueMapping.getOrPut(to.get(), ::mutableSetOf)
        for (key in fromKeys) {
            from.get().removeUser(this)
            locals[key] = to.get()
            to.addUser(this)
            toKeys.add(key)
        }
        fromKeys.clear()
    }

    override fun clearUses(ctx: UsageContext) {
        entries.forEach { it.value.removeUser(this) }
    }
}
