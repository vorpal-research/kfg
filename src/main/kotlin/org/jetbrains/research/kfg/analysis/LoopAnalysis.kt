package org.jetbrains.research.kfg.analysis

import org.jetbrains.research.kfg.IF
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.BodyBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.PhiInst
import org.jetbrains.research.kfg.ir.value.instruction.TerminateInst
import org.jetbrains.research.kfg.util.GraphNode
import org.jetbrains.research.kfg.util.LoopDetector
import org.jetbrains.research.kfg.visitor.MethodVisitor

class Loop(val header: BasicBlock, val body: Set<BasicBlock>) {
    fun getExitingBlocks() = body.filter { !body.containsAll(it.successors) }.toSet()
    fun hasPreheader() = header.predecessors.filter { !body.contains(it) }.size == 1
    fun getPreheader() = header.predecessors.first { !body.contains(it) }
    fun getLatches() = body.filter { it.successors.contains(header) }.toSet()
    fun contains(bb: BasicBlock) = body.contains(bb)
}

class LoopAnalysis(method: Method) : MethodVisitor(method) {
    val loops = mutableListOf<Loop>()
    override fun visit() {
        val nodes = method.basicBlocks.map { it as GraphNode }.toSet()
        LoopDetector(nodes).search()
                .map { Loop(it.key as BasicBlock, it.value.map { it as BasicBlock }.toSet()) }
                .forEach { loops.add(it) }
    }
}

class LoopPreheaderBuilder(method: Method, val loops: List<Loop>) : MethodVisitor(method) {
    override fun visit() {
        loops.filter { !it.hasPreheader() }.forEach { visitLoop(it) }
    }

    private fun visitLoop(loop: Loop) {
        val loopPredecessors = loop.header.predecessors.filter { !loop.contains(it) }
        if (loopPredecessors.size == 1) return

        val preheader = BodyBlock("loop.preheader")
        loopPredecessors.forEach {
            it.removeSuccessor(loop.header)
            loop.header.removePredecessor(it)
            it.addSuccessor(preheader)
            preheader.addPredecessor(it)
            (it.back() as TerminateInst).replaceUsesOf(loop.header, preheader)
        }
        preheader.addSuccessor(loop.header)
        loop.header.addPredecessor(preheader)
        loop.header.instructions.mapNotNull { it as? PhiInst }.forEach {
            val predKeys = it.getIncomings().filter { it.key in loopPredecessors }
            val predValues = predKeys.values.toSet()
            val preheaderVal = if (predValues.size == 1) predValues.first() else {
                val phi = IF.getPhi(it.type, predKeys)
                preheader.addInstruction(phi)
                phi
            }

            val noPredKeys = it.getIncomings().filter { it.key !in loopPredecessors }.toMutableMap()
            noPredKeys[preheader] = preheaderVal
            val newHeaderPhi = IF.getPhi(it.type, noPredKeys)
            loop.header.insertBefore(it, newHeaderPhi)
            it.replaceAllUsesWith(newHeaderPhi)
            loop.header.remove(it)
        }
        preheader.addInstruction(IF.getJump(loop.header))
        method.addBefore(loop.header, preheader)
    }
}