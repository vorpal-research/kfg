package org.jetbrains.research.kfg.visitor

import org.jetbrains.research.kfg.analysis.Loop
import org.jetbrains.research.kfg.analysis.LoopManager
import org.jetbrains.research.kfg.ir.Method

interface LoopVisitor {
    fun visit(method: Method) {
        val loops: List<Loop> = LoopManager.getMethodLoopInfo(method)
        loops.forEach { visitLoop(it) }
        updateLoopInfo(method)
    }

    fun visitLoop(loop: Loop) {
        for (it in loop.subloops) visitLoop(it)
    }

    fun preservesLoopInfo() = false

    fun updateLoopInfo(method: Method) {
        if (!this.preservesLoopInfo()) {
            LoopManager.setInvalid(method)
        }
    }
}