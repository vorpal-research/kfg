package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.visitor.Loop
import org.jetbrains.research.kfg.visitor.performLoopAnalysis

private class LoopInfo(val loops: List<Loop>, var valid: Boolean) {
    constructor() : this(listOf(), false)
    constructor(loops: List<Loop>) : this(loops, true)
}

internal interface LoopManager {
    fun invalidate()
    fun setInvalid(method: Method)
    fun getMethodLoopInfo(method: Method): List<Loop>
}

internal class DefaultLoopManager : LoopManager {
    override fun invalidate() {}
    override fun setInvalid(method: Method) {}
    override fun getMethodLoopInfo(method: Method) = performLoopAnalysis(method)
}

internal class CachingLoopManager(val cm: ClassManager) : LoopManager {
    private val loopInfo = mutableMapOf<Method, LoopInfo>()

    override fun invalidate() {
        for ((_, info) in loopInfo) {
            info.valid = false
        }
    }

    override fun setInvalid(method: Method) {
        loopInfo.getOrPut(method) { LoopInfo() }.valid = false
    }

    override fun getMethodLoopInfo(method: Method): List<Loop> {
        val info = loopInfo.getOrPut(method) { LoopInfo() }
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