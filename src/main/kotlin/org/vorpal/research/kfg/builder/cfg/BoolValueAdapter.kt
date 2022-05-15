package org.vorpal.research.kfg.builder.cfg

import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.ValueFactory
import org.vorpal.research.kfg.ir.value.instruction.*
import org.vorpal.research.kfg.type.ArrayType
import org.vorpal.research.kfg.type.BoolType
import org.vorpal.research.kfg.type.Integral
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kfg.visitor.MethodVisitor
import org.vorpal.research.kfg.visitor.Pipeline
import org.vorpal.research.kthelper.assert.unreachable

class BoolValueAdapter(override val cm: ClassManager, override val ctx: UsageContext) : MethodVisitor, InstructionBuilder {
    override val instructions: InstructionFactory
        get() = cm.instruction
    override val types: TypeFactory
        get() = cm.type
    override val values: ValueFactory
        get() = cm.value


    private val _pipeline = object : Pipeline(cm) {
        override fun runInternal() {
            // Do nothing
        }
    }
    override val pipeline: Pipeline get() = _pipeline

    override fun cleanup() {}

    override fun visitArrayStoreInst(inst: ArrayStoreInst) {
        val bb = inst.parent

        val arrayType = inst.arrayRef.type as? ArrayType
                ?: unreachable("Non-array type of array store reference")

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