package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.SlotTracker

interface SlotTrackerContext {

    val Method.slotTracker: SlotTracker

    fun initTracker(method: Method)

}

class SlotTrackerContextImpl : SlotTrackerContext {
    private val trackers = mutableMapOf<Method, SlotTracker>()
    override val Method.slotTracker: SlotTracker
        get() = trackers.getOrPut(this) { SlotTracker(this) }

    override fun initTracker(method: Method) {
        trackers.getOrPut(method) { SlotTracker(method) }
    }

    fun clear() {
        trackers.values.forEach { it.clear() }
        trackers.clear()
    }
}

fun SlotTrackerContext() = SlotTrackerContextImpl()


fun <T : Any> withSlotTracker(body: SlotTrackerContext.() -> T): T = SlotTrackerContext().body()

fun <T : Any> withSlotTracker(ctx: SlotTrackerContext, body: SlotTrackerContext.() -> T): T = ctx.body()
