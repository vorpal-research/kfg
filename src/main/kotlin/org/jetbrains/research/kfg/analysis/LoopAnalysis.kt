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

class Loop(val header: BasicBlock, internal val body: MutableSet<BasicBlock>) {
    fun getExitingBlocks() = body.filter { !body.containsAll(it.successors) }.toSet()
    fun hasPreheader() = header.predecessors.filter { !body.contains(it) }.size == 1
    fun getPreheader() = header.predecessors.first { !body.contains(it) }
    fun getLatches() = body.filter { it.successors.contains(header) }.toSet()
    fun contains(bb: BasicBlock) = body.contains(bb)

    fun addBlock(bb: BasicBlock) = body.add(bb)
}

class LoopAnalysis(method: Method) : MethodVisitor(method) {
    val loops = mutableListOf<Loop>()
    override fun visit() {
        val nodes = method.basicBlocks.map { it as GraphNode }.toSet()
        LoopDetector(nodes).search()
                .map { Loop(it.key as BasicBlock, it.value.map { it as BasicBlock }.toMutableSet()) }
                .forEach { loops.add(it) }
    }
}

class LoopSimplifier(method: Method, val loops: List<Loop>) : MethodVisitor(method) {
    override fun visit() {
        loops.forEach {
            buildPreheader(it)
            buildLatch(it)
        }
        IRVerifier(method).visit()
    }

    private fun remapBlocks(target: BasicBlock, from: BasicBlock, to: BasicBlock) {
        target.removeSuccessor(from)
        from.removePredecessor(target)
        target.addSuccessor(to)
        from.addPredecessor(target)
        (target.back() as TerminateInst).replaceUsesOf(from, to)
    }

    private fun remapPhis(target: BasicBlock, from: Set<BasicBlock>, to: BasicBlock) {
        target.instructions.mapNotNull { it as? PhiInst }.forEach {
            val fromIncomings = it.getIncomings().filter { it.key in from }
            val fromValues = fromIncomings.values.toSet()
            val toValue = if (fromValues.size == 1) fromValues.first() else {
                val phi = IF.getPhi(it.type, fromIncomings)
                to.addInstruction(phi)
                phi
            }

            val targetIncomings = it.getIncomings().filter { it.key !in from }.toMutableMap()
            targetIncomings[to] = toValue
            val targetPhi = IF.getPhi(it.type, targetIncomings)
            target.insertBefore(it, targetPhi)
            it.replaceAllUsesWith(targetPhi)
            target.remove(it)
        }
    }

    private fun buildPreheader(loop: Loop) {
        val header = loop.header
        val loopPredecessors = header.predecessors.filter { !loop.contains(it) }.toSet()
        if (loopPredecessors.size == 1) return

        val preheader = BodyBlock("loop.preheader")
        loopPredecessors.forEach { remapBlocks(it, header, preheader) }
        preheader.addSuccessor(header)
        header.addPredecessor(preheader)

        remapPhis(header, loopPredecessors, preheader)
        preheader.addInstruction(IF.getJump(header))
        method.addBefore(header, preheader)
    }

    private fun buildLatch(loop: Loop) {
        val latches = loop.getLatches()
        if (latches.size == 1) return

        val header = loop.header
        val latch = BodyBlock("loop.latch")
        latches.forEach { remapBlocks(it, header, latch) }
        latch.addSuccessor(header)
        header.addPredecessor(latch)

        remapPhis(header, latches, latch)
        latch.addInstruction(IF.getJump(header))
        method.add(latch)
        loop.addBlock(latch)
    }
}