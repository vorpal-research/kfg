package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.Name
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.Type

class NewArrayInst(name: Name, type: Type, dimentions: Array<Value>): Instruction(name, type, dimentions) {
    val component: Type

    init {
        var current = type
        repeat(numDimensions()) {
            assert(current is ArrayType)
            current = (current as ArrayType).component
        }
        this.component = current
    }

    fun getDimensions() = operands.toList()
    fun numDimensions() = operands.size

    override fun print(): String {
        val sb = StringBuilder()
        sb.append("$name = new ${component.name}")
        getDimensions().forEach {
            sb.append("[$it]")
        }
        return sb.toString()
    }
    override fun clone(): Instruction = NewArrayInst(name.clone(), type, operands)
}