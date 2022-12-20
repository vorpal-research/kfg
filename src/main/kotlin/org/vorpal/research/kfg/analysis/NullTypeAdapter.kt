package org.vorpal.research.kfg.analysis

import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.KfgException
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.instruction.PhiInst
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kfg.type.mergeTypes
import org.vorpal.research.kfg.visitor.MethodVisitor

class TypeMergeFailedException(val types: Set<Type>) : KfgException()

class NullTypeAdapter(override val cm: ClassManager, val ctx: UsageContext) : MethodVisitor {
    override fun cleanup() {}

    override fun visitPhiInst(inst: PhiInst) = with(ctx) {
        if (inst.type == types.nullType) {
            val incomingTypes = inst.incomingValues.mapTo(mutableSetOf()) { it.type }
            val actualType = mergeTypes(types, incomingTypes) ?: throw TypeMergeFailedException(incomingTypes)
            val newPhi = inst(cm) { phi(actualType, inst.incomings) }
            inst.parent.insertBefore(inst, newPhi)
            inst.replaceAllUsesWith(newPhi)
            inst.clearUses()
            inst.parent -= inst
        }
    }
}
