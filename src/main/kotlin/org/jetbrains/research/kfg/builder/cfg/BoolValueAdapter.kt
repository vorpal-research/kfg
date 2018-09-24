package org.jetbrains.research.kfg.builder.cfg

import org.jetbrains.research.kfg.IF
import org.jetbrains.research.kfg.InvalidStateError
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.instruction.ArrayStoreInst
import org.jetbrains.research.kfg.ir.value.instruction.FieldStoreInst
import org.jetbrains.research.kfg.ir.value.instruction.ReturnInst
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.BoolType
import org.jetbrains.research.kfg.type.Integral
import org.jetbrains.research.kfg.visitor.MethodVisitor

object BoolValueAdapter : MethodVisitor {
    override fun cleanup() {}

    override fun visitArrayStoreInst(inst: ArrayStoreInst) {
        val bb = inst.parent ?: throw InvalidStateError("No parent of method instruction")

        val arrayType = inst.arrayRef.type as? ArrayType
                ?: throw InvalidStateError("Non-array type of array store reference")

        if (arrayType.component === BoolType && inst.value.type is Integral) {
            val cast = IF.getCast(TF.boolType, inst.value)
            bb.insertBefore(inst, cast)
            inst.replaceUsesOf(from = inst.value, to = cast)
        }
    }

    override fun visitFieldStoreInst(inst: FieldStoreInst) {
        val bb = inst.parent ?: throw InvalidStateError("No parent of method instruction")

        if (inst.type === BoolType && inst.value.type is Integral) {
            val cast = IF.getCast(TF.boolType, inst.value)
            bb.insertBefore(inst, cast)
            inst.replaceUsesOf(from = inst.value, to = cast)
        }
    }

    override fun visitReturnInst(inst: ReturnInst) {
        val bb = inst.parent ?: throw InvalidStateError("No parent of method instruction")
        val method = bb.parent ?: throw InvalidStateError("No parent of basic block")

        if (method.returnType === BoolType && inst.returnValue.type !== BoolType) {
            val cast = IF.getCast(TF.boolType, inst.returnValue)
            bb.insertBefore(inst, cast)
            inst.replaceUsesOf(from = inst.returnValue, to = cast)
        }
    }
}