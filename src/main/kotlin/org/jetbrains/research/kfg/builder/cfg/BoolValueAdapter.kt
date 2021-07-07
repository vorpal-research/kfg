package org.jetbrains.research.kfg.builder.cfg

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.value.UsageContext
import org.jetbrains.research.kfg.ir.value.ValueFactory
import org.jetbrains.research.kfg.ir.value.instruction.*
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.BoolType
import org.jetbrains.research.kfg.type.Integral
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kthelper.assert.unreachable
import org.jetbrains.research.kthelper.logging.log

class BoolValueAdapter(override val cm: ClassManager, override val ctx: UsageContext) : MethodVisitor, InstructionBuilder {
    override val instructions: InstructionFactory
        get() = cm.instruction
    override val types: TypeFactory
        get() = cm.type
    override val values: ValueFactory
        get() = cm.value

    override fun cleanup() {}

    override fun visitArrayStoreInst(inst: ArrayStoreInst) {
        val bb = inst.parent

        val arrayType = inst.arrayRef.type as? ArrayType
                ?: unreachable { log.error("Non-array type of array store reference") }

        if (arrayType.component is BoolType && inst.value.type is Integral) {
            val cast = inst.value `as` types.boolType
            bb.insertBefore(inst, cast)
            inst.replaceUsesOf(ctx, from = inst.value, to = cast)
        }
    }

    override fun visitFieldStoreInst(inst: FieldStoreInst) {
        val bb = inst.parent

        if (inst.type is BoolType && inst.value.type is Integral) {
            val cast = inst.value `as` types.boolType
            bb.insertBefore(inst, cast)
            inst.replaceUsesOf(ctx, from = inst.value, to = cast)
        }
    }

    override fun visitReturnInst(inst: ReturnInst) {
        val bb = inst.parent
        val method = bb.parent

        if (method.returnType is BoolType && inst.returnValue.type !is BoolType) {
            val cast = inst.returnValue `as` types.boolType
            bb.insertBefore(inst, cast)
            inst.replaceUsesOf(ctx, from = inst.returnValue, to = cast)
        }
    }
}