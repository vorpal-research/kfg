package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.Name

class CastInst(name: Name, type: Type, obj: Value) : Instruction(name, type, arrayOf(obj)) {

    val operand: Value
        get() = ops[0]

    override fun print()= "$name = (${type.name}) $operand"
    override fun clone(): Instruction = CastInst(name.clone(), type, operand)
}