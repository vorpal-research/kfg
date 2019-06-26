package org.jetbrains.research.kfg.analysis

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.BodyBlock
import org.jetbrains.research.kfg.ir.CatchBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.PhiInst

class LoopSimplifier(override val cm: ClassManager) : LoopVisitor {
    private var current: Method? = null

    override val preservesLoopInfo get() = false

    override fun cleanup() {}

    override fun visit(method: Method) {
        current = method
        super.visit(method)
        IRVerifier(cm).visit(method)
        current = null
    }

    override fun visit(loop: Loop) {
        super.visit(loop)
        buildPreheader(loop)
        buildLatch(loop)
    }

    private fun remapBlocks(target: BasicBlock, from: BasicBlock, to: BasicBlock) {
        target.removeSuccessor(from)
        from.removePredecessor(target)
        target.addSuccessor(to)
        to.addPredecessor(target)
        target.terminator.replaceUsesOf(from, to)
    }

    private fun remapPhis(target: BasicBlock, from: Set<BasicBlock>, to: BasicBlock) {
        target.instructions.mapNotNull { it as? PhiInst }.forEach { phi ->
            val fromIncomings = phi.incomings.filter { it.key in from }
            val fromValues = fromIncomings.values.toSet()
            val toValue = when {
                fromValues.size == 1 -> fromValues.first()
                else -> {
                    val newPhi = instructions.getPhi(phi.type, fromIncomings)
                    to += newPhi
                    newPhi
                }
            }

            val targetIncomings = phi.incomings.filter { it.key !in from }.toMutableMap()
            targetIncomings[to] = toValue
            val targetPhi = instructions.getPhi(phi.type, targetIncomings)
            target.insertBefore(phi, targetPhi)
            phi.replaceAllUsesWith(targetPhi)
            target -= phi
        }
    }

    private fun mapToCatch(original: BasicBlock, new: BasicBlock, catch: CatchBlock) {
        catch.addThrowers(listOf(new))
        new.addHandler(catch)

        catch.mapNotNull { it as? PhiInst }.forEach { phi ->
            val incomings = phi.incomings.toMutableMap()
            incomings[new] = incomings[original]!!
            val newPhi = instructions.getPhi(phi.type, incomings)
            catch.insertBefore(phi, newPhi)
            phi.replaceAllUsesWith(newPhi)
            catch -= phi
        }
    }

    private fun buildPreheader(loop: Loop) {
        val header = loop.header
        val loopPredecessors = header.predecessors.filter { it !in loop }.toSet()
        if (loopPredecessors.size == 1) return

        val preheader = BodyBlock("loop.preheader")
        loopPredecessors.forEach { remapBlocks(it, header, preheader) }
        preheader.addSuccessor(header)
        header.addPredecessor(preheader)

        remapPhis(header, loopPredecessors, preheader)
        header.handlers.forEach { mapToCatch(header, preheader, it) }
        preheader += instructions.getJump(header)
        current!!.addBefore(header, preheader)
    }

    private fun buildLatch(loop: Loop) {
        val header = loop.header
        val latches = loop.body.filter { it.successors.contains(header) }.toSet()
        if (latches.size == 1) return

        val latch = BodyBlock("loop.latch")
        latches.forEach { remapBlocks(it, header, latch) }
        latch.addSuccessor(header)
        header.addPredecessor(latch)

        remapPhis(header, latches, latch)
        latch += instructions.getJump(header)
        current!!.add(latch)
        loop.addBlock(latch)
    }
}