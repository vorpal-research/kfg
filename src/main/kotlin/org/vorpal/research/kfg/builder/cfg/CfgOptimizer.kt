package org.vorpal.research.kfg.builder.cfg

import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.CatchBlock
import org.vorpal.research.kfg.ir.MethodBody
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.instruction.PhiInst
import org.vorpal.research.kfg.visitor.MethodVisitor

class CfgOptimizer(override val cm: ClassManager, val ctx: UsageContext) : MethodVisitor {
    override fun cleanup() {}

    override fun visitBody(body: MethodBody) = with (ctx) {
        for (block in body.basicBlocks.toList()) {
            if (block.isEmpty) continue
            if (block.size > 1) continue
            if (block.successors.size != 1) continue
            if (block.predecessors.size != 1) continue
            if (block.predecessors.first().successors.size != 1) continue
            if (block.successors.first().predecessors.size != 1) continue

            val successor = block.successors.first()
            val predecessor = block.predecessors.first()
            if (block.handlers != successor.handlers) continue

            val handlers = block.handlers
            for (catch in handlers) {
                catch.removeThrower(block)
            }

            val blockPhiUsers = block.users.mapNotNull { it as? PhiInst }
            for (phi in blockPhiUsers) {
                val parent = phi.parentUnsafe ?: continue

                val oldIncomings = phi.incomings
                val incomings = when (parent) {
                    is CatchBlock -> when (block) {
                        in parent.entries -> oldIncomings.map {
                            (if (it.key == block) predecessor else it.key) to it.value
                        }.toMap()
                        else -> oldIncomings.mapNotNull {
                            if (it.key == block) null else it.key to it.value
                        }.toMap()
                    }
                    else -> oldIncomings.map {
                        (if (it.key == block) predecessor else it.key) to it.value
                    }.toMap()
                }
                val newPhi = inst(cm) { phi(phi.type, incomings) }

                phi.replaceAllUsesWith(newPhi)
                parent.replace(phi, newPhi)
                phi.clearAllUses()
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
    }
}
