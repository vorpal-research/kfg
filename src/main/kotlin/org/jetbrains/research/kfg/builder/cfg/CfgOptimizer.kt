package org.jetbrains.research.kfg.builder.cfg

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.PhiInst
import org.jetbrains.research.kfg.visitor.MethodVisitor

class CfgOptimizer(override val cm: ClassManager) : MethodVisitor {
    override fun cleanup() {}

    override fun visit(method: Method) {
        super.visit(method)

        for (block in method.basicBlocks.toList()) {
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
            method.remove(block)
            predecessor.removeSuccessor(block)
            block.removeSuccessor(successor)
            predecessor.addSuccessor(successor)
            successor.addPredecessor(predecessor)
            block.replaceAllUsesWith(successor)

            for (phi in successor.mapNotNull { it as? PhiInst }) {
                val incomings = phi.incomings.map { if (it.key == block) predecessor to it.value else it.key to it.value }.toMap()

                val newPhi = cm.instruction.getPhi(phi.type, incomings)

                phi.replaceAllUsesWith(newPhi)
                successor.replace(phi, newPhi)
            }

            for (catch in handlers) {
                for (phi in catch.mapNotNull { it as? PhiInst }) {
                    val incomings = phi.incomings.toMutableMap()
                    incomings.remove(block)
                    val newPhi = cm.instruction.getPhi(phi.type, incomings)

                    phi.replaceAllUsesWith(newPhi)
                    catch.replace(phi, newPhi)
                }
            }

        }
    }
}