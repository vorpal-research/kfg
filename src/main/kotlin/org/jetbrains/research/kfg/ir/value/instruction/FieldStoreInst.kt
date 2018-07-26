package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.InvalidAccessError
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.Field

class FieldStoreInst : Instruction {
    val field: Field
    val isStatic: Boolean

    constructor(field: Field, value: Value)
            : super(UndefinedName, field.type, arrayOf(value)) {
        this.field = field
        isStatic = true
    }

    constructor(owner: Value, field: Field, value: Value)
            : super(UndefinedName, field.type, arrayOf(owner, value)) {
        this.field = field
        isStatic = false
    }

    val hasOwner: Boolean
        get() = !isStatic

    val owner: Value
        get() = when {
            hasOwner -> ops[0]
            else -> throw InvalidAccessError("Trying to get owner of static field")
        }

    val value: Value
        get() = if (hasOwner) ops[1] else ops[0]

    override fun print(): String {
        val sb = StringBuilder()
        if (hasOwner) sb.append("$owner.")
        else sb.append("${field.`class`.name}.")
        sb.append("${field.name} = $value")
        return sb.toString()
    }

    override fun clone(): Instruction = when {
        isStatic -> FieldStoreInst(field, value)
        else -> FieldStoreInst(owner, field, value)
    }
}