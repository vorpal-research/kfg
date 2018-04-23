package org.jetbrains.research.kfg.visitor

import org.jetbrains.research.kfg.analysis.Loop
import org.jetbrains.research.kfg.analysis.LoopManager
import org.jetbrains.research.kfg.ir.Method

open class LoopVisitor(val method: Method) : NodeVisitor(method) {
    val loops: List<Loop> = LoopManager.getMethodLoopInfo(method)

    override fun visit() {
        loops.forEach { visitLoop(it) }
        updateLoopInfo()
    }

    open fun visitLoop(loop: Loop) {
        for (it in loop.subloops) visitLoop(it)
    }

    open fun preservesLoopInfo() = false

    fun updateLoopInfo() {
        if (!this.preservesLoopInfo()) {
            LoopManager.setInvalid(method)
        }
    }
}