package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.ir.value.Value

enum class UnaryOpcode {
    NEG,
    LENGTH
}

class UnaryInst(name: String, val opcode: UnaryOpcode, obj: Value)
    : Instruction(name, TypeFactory.instance.getIntType(), arrayOf(obj)) {

    fun getOperand() = operands[0]

    override fun print(): String {
        val sb = StringBuilder()
        sb.append("$name = ")
        sb.append(if (opcode == UnaryOpcode.LENGTH) "${getOperand()}.length" else "-${getOperand()}")
        return sb.toString()
    }
}