package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.analysis.Loop
import org.jetbrains.research.kfg.analysis.performLoopAnalysis
import org.jetbrains.research.kfg.ir.Method

internal class LoopManager(val cm: ClassManager) {
    private class LoopInfo(val loops: List<Loop>, var valid: Boolean) {
        constructor() : this(listOf(), false)
        constructor(loops: List<Loop>) : this(loops, true)
    }

    private val loopInfo = mutableMapOf<Method, LoopInfo>()

    fun setInvalid(method: Method) {
        loopInfo.getOrPut(method, LoopManager::LoopInfo).valid = false
    }

    fun getMethodLoopInfo(method: Method): List<Loop> {
        val info = loopInfo.getOrPut(method, LoopManager::LoopInfo)
        return when {
            info.valid -> info.loops
            else -> {
                val loops = performLoopAnalysis(method)
                loopInfo[method] = LoopInfo(loops)
                loops
            }
        }
    }
}