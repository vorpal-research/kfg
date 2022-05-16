package org.vorpal.research.kfg.builder.cfg

import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.MethodBody
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.visitor.MethodVisitor

class ThrowCatchNormalizer(override val cm: ClassManager, val ctx: UsageContext) : MethodVisitor {
    override fun cleanup() {}

    override fun visitBody(body: MethodBody) = with(ctx) {
        for (block in body.basicBlocks.toList()) {
            if (block.size > 1) continue
            if (block.successors.size != 1) continue
            if (block.predecessors.size != 1) continue

            val successor = block.successors.first()
            val predecessor = block.predecessors.first()
            if (predecessor.handlers != successor.handlers) continue
            for (handler in successor.handlers intersect predecessor.handlers) {
                block.linkThrowing(handler)
            }
        }
    }
}