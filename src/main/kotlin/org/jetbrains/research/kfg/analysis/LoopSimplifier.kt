package org.jetbrains.research.kfg.analysis

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.BodyBlock
import org.jetbrains.research.kfg.ir.CatchBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.MethodUsageContext
import org.jetbrains.research.kfg.ir.value.instruction.PhiInst
import org.jetbrains.research.kfg.ir.value.usageContext
import org.jetbrains.research.kfg.visitor.Loop
import org.jetbrains.research.kfg.visitor.LoopVisitor
import org.jetbrains.research.kthelper.KtException

class LoopSimplifier(override val cm: ClassManager) : LoopVisitor {
    private lateinit var current: Method
    private lateinit var ctx: MethodUsageContext

    override val preservesLoopInfo get() = false

    override fun cleanup() {}

    override fun visit(method: Method) {
        if (!method.hasBody) return
        current = method
        current.usageContext.also { ctx = it }.use {
            super.visit(method)
            IRVerifier(cm, it).visit(method)
        }
    }

    override fun visitLoop(loop: Loop) {
        super.visitLoop(loop)
        if (loop.allEntries.size != 1) {
            if (cm.failOnError) throw KtException("Can't simplify loop with multiple entries")
            else return
        }
        buildPreheader(loop)
        buildLatch(loop)
    }

    private fun remapBlocks(target: BasicBlock, from: BasicBlock, to: BasicBlock) = with(ctx) {
        target.unlinkForward(from)
        target.linkForward(to)
        target.terminator.replaceUsesOf(from, to)
    }

    private fun remapPhis(target: BasicBlock, from: Set<BasicBlock>, to: BasicBlock) = with(ctx) {
        for (phi in target.instructions.mapNotNull { it as? PhiInst }) {
            val fromIncomings = phi.incomings.filter { it.key in from }
            val fromValues = fromIncomings.values.toSet()
            val toValue = when (fromValues.size) {
                1 -> fromValues.first()
                else -> inst(cm) {
                    phi(phi.type, fromIncomings).also {
                        to += it
                    }
                }
            }

            val targetIncomings = phi.incomings.filter { it.key !in from }.toMutableMap()
            targetIncomings[to] = toValue
            val targetPhi = inst(cm) { phi(phi.type, targetIncomings) }
            target.insertBefore(phi, targetPhi)
            phi.replaceAllUsesWith(targetPhi)
            phi.clearUses()
            target -= phi
        }
    }

    private fun mapToCatch(original: BasicBlock, new: BasicBlock, catch: CatchBlock) = with(ctx) {
        new.linkThrowing(catch)

        for (phi in catch.mapNotNull { it as? PhiInst }) {
            val incomings = phi.incomings.toMutableMap()
            incomings[new] = incomings[original]!!
            val newPhi = inst(cm) { phi(phi.type, incomings) }
            catch.insertBefore(phi, newPhi)
            phi.replaceAllUsesWith(newPhi)
            phi.clearUses()
            catch -= phi
        }
    }

    private fun buildPreheader(loop: Loop) = with(ctx) {
        val header = loop.header
        val loopPredecessors = header.predecessors.filter { it !in loop }.toSet()
        if (loopPredecessors.size == 1) return

        val preheader = BodyBlock("loop.preheader")
        header.handlers.forEach { mapToCatch(header, preheader, it) }
        loopPredecessors.forEach { remapBlocks(it, header, preheader) }
        preheader.linkForward(header)

        remapPhis(header, loopPredecessors, preheader)
        preheader += inst(cm) { goto(header) }
        current.addBefore(header, preheader)
        if (loop.hasParent) {
            loop.parent.addBlock(preheader)
        }
    }

    private fun buildLatch(loop: Loop) = with(ctx) {
        val header = loop.header
        val latches = loop.latches
        if (latches.size == 1) return

        val latch = BodyBlock("loop.latch")
        latches.forEach {
            remapBlocks(it, header, latch)
            it.handlers.forEach { catch -> mapToCatch(it, latch, catch) }
        }
        latch.linkForward(header)

        remapPhis(header, latches, latch)

        latch += inst(cm) { goto(header) }
        current.addAfter(latches.first(), latch)
        loop.addBlock(latch)
    }
}