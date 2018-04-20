package org.jetbrains.research.kfg.visitor

import org.jetbrains.research.kfg.analysis.Loop
import org.jetbrains.research.kfg.analysis.LoopAnalysis
import org.jetbrains.research.kfg.ir.Method

open class LoopVisitor(val method: Method) : NodeVisitor(method) {
    val loops: List<Loop>

    init {
        val loopInfo = LoopAnalysis(method)
        loopInfo.visit()
        loops = loopInfo.loops

    }

    override fun visit() {
        loops.forEach { visitLoop(it) }
    }

    open fun visitLoop(loop: Loop) {
        for (it in loop.subloops) visitLoop(it)
    }
}