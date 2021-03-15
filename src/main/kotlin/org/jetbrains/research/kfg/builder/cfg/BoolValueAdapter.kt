package org.jetbrains.research.kfg.builder.cfg

import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.value.instruction.ArrayStoreInst
import org.jetbrains.research.kfg.ir.value.instruction.FieldStoreInst
import org.jetbrains.research.kfg.ir.value.instruction.ReturnInst
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.BoolType
import org.jetbrains.research.kfg.type.Integral
import org.jetbrains.research.kfg.visitor.MethodVisitor

class BoolValueAdapter(override val cm: ClassManager) : MethodVisitor {
    override fun cleanup() {}

    override fun visitArrayStoreInst(inst: ArrayStoreInst) {
        val bb = inst.parent

        val arrayType = inst.arrayRef.type as? ArrayType
                ?: unreachable { log.error("Non-array type of array store reference") }

        if (arrayType.component is BoolType && inst.value.type is Integral) {
            val cast = instructions.getCast(types.boolType, inst.value)
            bb.insertBefore(inst, cast)
            inst.replaceUsesOf(from = inst.value, to = cast)
        }
    }

    override fun visitFieldStoreInst(inst: FieldStoreInst) {
        val bb = inst.parent

        if (inst.type is BoolType && inst.value.type is Integral) {
            val cast = instructions.getCast(types.boolType, inst.value)
            bb.insertBefore(inst, cast)
            inst.replaceUsesOf(from = inst.value, to = cast)
        }
    }

    override fun visitReturnInst(inst: ReturnInst) {
        val bb = inst.parent
        val method = bb.parent

        if (method.returnType is BoolType && inst.returnValue.type !is BoolType) {
            val cast = instructions.getCast(types.boolType, inst.returnValue)
            bb.insertBefore(inst, cast)
            inst.replaceUsesOf(from = inst.returnValue, to = cast)
        }
    }
}