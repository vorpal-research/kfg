package org.vorpal.research.kfg.builder.cfg

import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.CatchBlock
import org.vorpal.research.kfg.ir.MethodBody
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.instruction.PhiInst
import org.vorpal.research.kfg.visitor.MethodVisitor

class CfgOptimizer(override val cm: ClassManager, val ctx: UsageContext) : MethodVisitor {
    override fun cleanup() {}

    private val BasicBlock.canBeOptimized: Boolean
        get() {
            if (this.isEmpty) return false
            if (this.size > 1) return false
            if (this.successors.size != 1) return false
            if (this.predecessors.size != 1) return false
            if (this.predecessors.first().successors.size != 1) return false
            if (this.successors.first().predecessors.size != 1) return false

            val successor = this.successors.first()
            if (this.handlers != successor.handlers) return false
            return true
        }

    override fun visitBody(body: MethodBody) = with(ctx) {
        val phiMappings = mutableMapOf<PhiInst, MutableMap<BasicBlock, BasicBlock?>>()
        for (block in body.basicBlocks.toList()) {
            if (!block.canBeOptimized) continue
            val successor = block.successors.first()
            val predecessor = block.predecessors.first()

            val handlers = block.handlers
            for (catch in handlers) {
                catch.removeThrower(block)
            }

            val blockPhiUsers = block.users.filterIsInstance<PhiInst>()
            for (phi in blockPhiUsers) {
                val parent = phi.parentUnsafe ?: continue

                when (parent) {
                    is CatchBlock -> when (block) {
                        in parent.entries -> phiMappings.getOrPut(phi, ::mutableMapOf)[block] = predecessor
                        else -> phiMappings.getOrPut(phi, ::mutableMapOf)[block] = null
                    }

                    else -> phiMappings.getOrPut(phi, ::mutableMapOf)[block] = predecessor
                }
            }

            body.remove(block)
            for (inst in block.toList()) {
                block.remove(inst)
                inst.clearAllUses()
            }
            predecessor.removeSuccessor(block)
            block.removeSuccessor(successor)
            predecessor.addSuccessor(successor)
            successor.addPredecessor(predecessor)
            block.replaceAllUsesWith(successor)
        }


        for ((phi, mappings) in phiMappings) {
            val parent = phi.parentUnsafe ?: continue
            val incomings = phi.incomings.mapNotNull {
                if (it.key in mappings) {
                    if (mappings[it.key] == null) null
                    else mappings[it.key]!! to it.value
                } else it.key to it.value
            }.toMap()
            val newPhi = inst(cm) { phi(phi.type, incomings) }

            phi.replaceAllUsesWith(newPhi)
            parent.replace(phi, newPhi)
            phi.clearAllUses()
        }
    }
}
