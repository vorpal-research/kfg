package org.vorpal.research.kfg.analysis

import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.BodyBlock
import org.vorpal.research.kfg.ir.CatchBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.MethodUsageContext
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.PhiInst
import org.vorpal.research.kfg.ir.value.usageContext
import org.vorpal.research.kfg.visitor.Loop
import org.vorpal.research.kfg.visitor.LoopVisitor
import org.vorpal.research.kthelper.KtException
import org.vorpal.research.kthelper.assert.unreachable

class LoopSimplifier(override val cm: ClassManager) : LoopVisitor {
    private lateinit var ctx: MethodUsageContext

    override val preservesLoopInfo get() = false

    override fun cleanup() {}

    override fun visit(method: Method) {
        if (!method.hasBody) return
        method.usageContext.also { ctx = it }.use {
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

    private fun parseNewIncoming(
        incomings: Map<BasicBlock, Value>,
        originals: Set<BasicBlock>,
        new: BasicBlock
    ): Value {
        val originalIncomings = incomings.filter { (key, _) -> key in originals }.toMap()
        return new.filterIsInstance<PhiInst>().firstOrNull { newPhi ->
            val incs = newPhi.incomings
            originalIncomings.all { (key, value) -> incs[key] == value }
        } ?: originalIncomings.values.single()
    }

    private fun mapToCatches(originals: Set<BasicBlock>, new: BasicBlock) = with(ctx) {
        for (catch in originals.flatMap { it.handlers }.toSet()) {
            new.linkThrowing(catch)

            for (phi in catch.mapNotNull { it as? PhiInst }) {
                val incomings = phi.incomings.toMutableMap()
                val newIncoming = parseNewIncoming(incomings, originals, new)
                incomings[new] = newIncoming
                val newPhi = inst(cm) { phi(phi.type, incomings) }
                catch.insertBefore(phi, newPhi)
                phi.replaceAllUsesWith(newPhi)
                phi.clearUses()
                catch -= phi
            }
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
        method.body.addBefore(header, preheader)
        if (loop.hasParent) {
            loop.parent.addBlock(preheader)
        }
    }

    // this is fucking hell, never look at this code
    private fun mapCatchEntries(
        originals: Set<BasicBlock>,
        new: BasicBlock,
        oldCatchEntries: Map<CatchBlock, Set<BasicBlock>>,
        newCatchEntries: Map<CatchBlock, Set<BasicBlock>>
    ) = with(ctx) {
        for (catch in method.body.catchEntries) {
            val newEntries = newCatchEntries[catch]!!
            val oldEntries = oldCatchEntries[catch]!!
            // if there is a difference --- we have changed the catch entries, and
            // we need to normalize all phis
            if (newEntries != oldEntries) {
                val newDiff = newEntries - oldEntries
                val oldDiff = oldEntries - newEntries
                when {
                    // first case --- all of old latches were entries of catch block,
                    // and they are replaced with single latch
                    oldDiff == originals && newDiff == setOf(new) -> {
                        for (phi in catch.filterIsInstance<PhiInst>()) {
                            val incomings = phi.incomings.toMutableMap()
                            val newIncoming = parseNewIncoming(incomings, originals, new)
                            originals.forEach { incomings.remove(it) }
                            incomings[new] = newIncoming
                            val newPhi = inst(cm) { phi(phi.type, incomings) }
                            catch.insertBefore(phi, newPhi)
                            phi.replaceAllUsesWith(newPhi)
                            phi.clearUses()
                            catch -= phi
                        }
                    }
                    // second case --- some of old latches became new entries because
                    // the new latch became thrower of the catch;
                    // some fucked up ship awaits you ahead
                    oldDiff.isEmpty() -> {
                        // first we need to find all values from old throwers that are used in catch body
                        // without phis
                        val catchBody = catch.body
                        val catchExits = catchBody.flatMap { it.successors }.filter { it !in catchBody }.toSet()
                        val bodyOperands = catchBody
                            .asSequence()
                            .flatten()
                            .filter { it !is PhiInst }
                            .flatten()
                            .filterIsInstance<Instruction>()
                            .filter { it.parent !in catchBody }
                            .toSet()

                        // for each external operand we need to manually add phi instruction
                        // with default mappings for new entries
                        for (operand in bodyOperands) {
                            // build incomings map for the new operand phi
                            val incomings = catch.allPredecessors.associateWith {
                                when (it) {
                                    // if this is new entry, map it to default
                                    in newDiff -> cm.value.getZero(operand.type)

                                    // if this is latch, we need to find (or create) corresponding phi for the operand
                                    new -> new.filterIsInstance<PhiInst>()
                                        .firstOrNull { phi -> operand in phi.incomingValues }
                                        ?: run {
                                            val newIncomings = new.predecessors.associateWith { pred ->
                                                if (pred in newDiff) cm.value.getZero(operand.type)
                                                else operand
                                            }
                                            val newPhiOperand = inst(cm) { phi(operand.type, newIncomings) }
                                            if (new.isEmpty) new.add(newPhiOperand)
                                            else new.insertBefore(new.first(), newPhiOperand)
                                            newPhiOperand
                                        }
                                    else -> operand
                                }
                            }
                            val newPhi = inst(cm) { phi(operand.type, incomings) }
                            catch.insertBefore(catch.first(), newPhi)

                            // replace all uses of operand in catch body and it's exits with the new phi
                            for (user in operand.users.toSet()) {
                                if (
                                    user is Instruction
                                    && (user.parent in catchBody || user.parent in catchExits)
                                    && user != newPhi
                                ) {
                                    user.replaceUsesOf(operand, newPhi)
                                }
                            }
                        }

                        // for each phi in catch add default mappings
                        for (phi in catch.filterIsInstance<PhiInst>()) {
                            val incomings = phi.incomings.toMutableMap()
                            val default = cm.value.getZero(phi.type)
                            newDiff.forEach { incomings[it] = default }
                            val newPhi = inst(cm) { phi(phi.type, incomings) }
                            catch.insertBefore(phi, newPhi)
                            phi.replaceAllUsesWith(newPhi)
                            phi.clearUses()
                            catch -= phi
                        }
                    }
                    else -> unreachable("Unexpected combination of catch entries change")
                }
            }
        }
    }

    private fun buildLatch(loop: Loop) = with(ctx) {
        val header = loop.header
        val latches = loop.latches
        if (latches.size == 1) return

        val oldCatchEntries = method.body.catchEntries.associateWith { it.entries }

        val latch = BodyBlock("loop.latch")
        remapPhis(header, latches, latch)
        latches.forEach {
            remapBlocks(it, header, latch)
        }
        mapToCatches(latches, latch)
        latch.linkForward(header)

        val newCatchEntries = method.body.catchEntries.associateWith { it.entries }
        mapCatchEntries(latches, latch, oldCatchEntries, newCatchEntries)

        latch += inst(cm) { goto(header) }
        method.body.addAfter(latches.first(), latch)
        loop.addBlock(latch)
    }
}
