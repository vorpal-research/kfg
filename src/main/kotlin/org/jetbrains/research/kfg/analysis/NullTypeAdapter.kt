package org.jetbrains.research.kfg.analysis

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.KfgException
import org.jetbrains.research.kfg.ir.value.instruction.PhiInst
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.mergeTypes
import org.jetbrains.research.kfg.visitor.MethodVisitor

class TypeMergeFailedException(val types: Set<Type>) : KfgException()

class NullTypeAdapter(override val cm: ClassManager) : MethodVisitor {
    override fun cleanup() {}

    override fun visitPhiInst(inst: PhiInst) {
        if (inst.type == types.nullType) {
            val incomingTypes = inst.incomingValues.map { it.type }.toSet()
            val actualType = mergeTypes(types, incomingTypes) ?: throw TypeMergeFailedException(incomingTypes)
            val newPhi = instructions.getPhi(actualType, inst.incomings)
            inst.parent.insertBefore(inst, newPhi)
            inst.replaceAllUsesWith(newPhi)
            inst.clearUses()
            inst.parent -= inst
        }
    }
}