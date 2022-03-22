package org.jetbrains.research.kfg.analysis

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.KfgException
import org.jetbrains.research.kfg.ir.value.UsageContext
import org.jetbrains.research.kfg.ir.value.instruction.PhiInst
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.mergeTypes
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kfg.visitor.Pipeline

class TypeMergeFailedException(val types: Set<Type>) : KfgException()

class NullTypeAdapter(override val cm: ClassManager, val ctx: UsageContext) : MethodVisitor {
    private val _pipeline = object : Pipeline(cm) {
        override fun runInternal() {
            // Do nothing
        }
    }
    override val pipeline: Pipeline get() = _pipeline

    override fun cleanup() {}

    override fun visitPhiInst(inst: PhiInst) = with(ctx) {
        if (inst.type == types.nullType) {
            val incomingTypes = inst.incomingValues.map { it.type }.toSet()
            val actualType = mergeTypes(types, incomingTypes) ?: throw TypeMergeFailedException(incomingTypes)
            val newPhi = inst(cm) { phi(actualType, inst.incomings) }
            inst.parent.insertBefore(inst, newPhi)
            inst.replaceAllUsesWith(newPhi)
            inst.clearUses()
            inst.parent -= inst
        }
    }
}