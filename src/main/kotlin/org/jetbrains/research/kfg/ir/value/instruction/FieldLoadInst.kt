package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.InvalidAccessError
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.Name

class FieldLoadInst : Instruction {
    val field: Field
    val isStatic: Boolean

    val hasOwner get() = !isStatic
    val owner get() = when {
        hasOwner -> ops[0]
        else -> throw InvalidAccessError("Trying to get owner of static field")
    }

    constructor(name: Name, field: Field) : super(name, field.type, arrayOf()) {
        this.field = field
        isStatic = true
    }

    constructor(name: Name, owner: Value, field: Field) : super(name, field.type, arrayOf(owner)) {
        this.field = field
        isStatic = false
    }

    override fun print(): String {
        val sb = StringBuilder()
        sb.append("$name = ")
        if (hasOwner) sb.append("$owner.")
        else sb.append("${field.`class`.name}.")
        sb.append(field.name)
        return sb.toString()
    }

    override fun clone(): Instruction = when {
        isStatic -> FieldLoadInst(name.clone(), field)
        else -> FieldLoadInst(name.clone(), owner, field)
    }
}