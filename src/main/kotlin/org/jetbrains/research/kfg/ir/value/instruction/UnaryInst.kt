package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.Name

enum class UnaryOpcode {
    NEG,
    LENGTH
}

class UnaryInst(name: Name, val opcode: UnaryOpcode, obj: Value)
    : Instruction(name, if (opcode == UnaryOpcode.LENGTH) TF.getIntType() else obj.type, arrayOf(obj)) {

    val operand: Value
        get() = ops[0]

    override fun print(): String {
        val sb = StringBuilder()
        sb.append("$name = ")
        sb.append(if (opcode == UnaryOpcode.LENGTH) "$operand.length" else "-$operand")
        return sb.toString()
    }

    override fun clone(): Instruction = UnaryInst(name.clone(), opcode, operand)
}