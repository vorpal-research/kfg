package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.Field
import org.vorpal.research.kfg.ir.value.Name
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kthelper.assert.asserted

class FieldLoadInst : Instruction {
    val field: Field
    val isStatic: Boolean

    val hasOwner: Boolean
        get() = !isStatic

    val owner: Value
        get() = asserted(hasOwner) { ops[0] }

    internal constructor(name: Name, field: Field, ctx: UsageContext) : super(name, field.type, mutableListOf(), ctx) {
        this.field = field
        isStatic = true
    }

    internal constructor(name: Name, owner: Value, field: Field, ctx: UsageContext) : super(
        name,
        field.type,
        mutableListOf(owner),
        ctx
    ) {
        this.field = field
        isStatic = false
    }

    override fun print(): String {
        val sb = StringBuilder()
        sb.append("$name = ")
        if (hasOwner) sb.append("$owner.")
        else sb.append("${field.klass.name}.")
        sb.append(field.name)
        return sb.toString()
    }

    override fun clone(ctx: UsageContext): Instruction = when {
        isStatic -> FieldLoadInst(name.clone(), field, ctx)
        else -> FieldLoadInst(name.clone(), owner, field, ctx)
    }
}
