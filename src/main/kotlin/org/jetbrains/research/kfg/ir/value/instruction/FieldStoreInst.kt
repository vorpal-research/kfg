package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.UsageContext
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kthelper.assert.asserted

class FieldStoreInst : Instruction {
    val field: Field
    val isStatic: Boolean

    internal constructor(field: Field, value: Value, ctx: UsageContext)
            : super(UndefinedName(), field.type, arrayOf(value), ctx) {
        this.field = field
        isStatic = true
    }

    internal constructor(owner: Value, field: Field, value: Value, ctx: UsageContext)
            : super(UndefinedName(), field.type, arrayOf(owner, value), ctx) {
        this.field = field
        isStatic = false
    }

    val hasOwner: Boolean
        get() = !isStatic

    val owner: Value
        get() = asserted(hasOwner) { ops[0] }

    val value: Value
        get() = if (hasOwner) ops[1] else ops[0]

    override fun print(): String {
        val sb = StringBuilder()
        if (hasOwner) sb.append("$owner.")
        else sb.append("${field.klass.name}.")
        sb.append("${field.name} = $value")
        return sb.toString()
    }

    override fun clone(ctx: UsageContext): Instruction = when {
        isStatic -> FieldStoreInst(field, value, ctx)
        else -> FieldStoreInst(owner, field, value, ctx)
    }
}