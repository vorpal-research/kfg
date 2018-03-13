package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.Field

class FieldStoreInst : Instruction {
    val field: Field
    val isStatic: Boolean

    constructor(field: Field, value: Value)
            : super(UndefinedName.instance, field.type, arrayOf(value)) {
        this.field = field
        isStatic = true
    }

    constructor(owner: Value, field: Field, value: Value)
            : super(UndefinedName.instance, field.type, arrayOf(owner, value)) {
        this.field = field
        isStatic = false
    }

    fun hasOwner() = !isStatic
    fun getOwner() = if (hasOwner()) operands[0] else null
    fun getValue() = if (hasOwner()) operands[1] else operands[0]

    override fun print(): String {
        val sb = StringBuilder()
        if (hasOwner()) sb.append("${getOwner()}.")
        else sb.append("${field.`class`.name}.")
        sb.append("${field.name} = ${getValue()}")
        return sb.toString()
    }
}