package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.Name

class FieldLoadInst : Instruction {
    val field: Field
    val isStatic: Boolean

    constructor(name: Name, field: Field) : super(name, field.type, arrayOf()) {
        this.field = field
        isStatic = true
    }

    constructor(name: Name, owner: Value, field: Field) : super(name, field.type, arrayOf(owner)) {
        this.field = field
        isStatic = false
    }

    fun hasOwner() = !isStatic
    fun getOwner() = if (hasOwner()) operands[0] else null
    override fun print(): String {
        val sb = StringBuilder()
        sb.append("$name = ")
        if (hasOwner()) sb.append("${getOwner()}.")
        else sb.append("${field.`class`.name}.")
        sb.append(field.name)
        return sb.toString()
    }

    override fun clone(): Instruction = if (isStatic) FieldLoadInst(name.clone(), field) else FieldLoadInst(name.clone(), getOwner()!!, field)
}