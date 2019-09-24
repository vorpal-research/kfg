package org.jetbrains.research.kfg.analysis

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.value.instruction.PhiInst
import org.jetbrains.research.kfg.type.mergeTypes
import org.jetbrains.research.kfg.visitor.MethodVisitor

class NullTypeAdapter(override val cm: ClassManager) : MethodVisitor {
    override fun cleanup() {}

    override fun visitPhiInst(inst: PhiInst) {
        if (inst.type == types.nullType) {
            val actualType = mergeTypes(types, inst.incomingValues.map { it.type }.toSet())
                    ?: throw IllegalStateException("Could not merge incoming types of phi inst")
            val newPhi = instructions.getPhi(actualType, inst.incomings)
            inst.parent?.insertBefore(inst, newPhi)
            inst.replaceAllUsesWith(newPhi)
        }
    }
}