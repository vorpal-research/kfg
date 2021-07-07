package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.Name
import org.jetbrains.research.kfg.ir.value.UsageContext
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type

enum class UnaryOpcode {
    NEG,
    LENGTH
}

class UnaryInst internal constructor(name: Name, type: Type, val opcode: UnaryOpcode, obj: Value, ctx: UsageContext) :
    Instruction(name, type, arrayOf(obj), ctx) {

    val operand: Value
        get() = ops[0]

    override fun print() = buildString {
        append("$name = ")
        append(if (opcode == UnaryOpcode.LENGTH) "$operand.length" else "-$operand")
    }

    override fun clone(ctx: UsageContext): Instruction = UnaryInst(name.clone(), type, opcode, operand, ctx)
}