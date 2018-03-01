package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.ValueName

class PhiInst(name: ValueName, type: Type, incomings: Map<BasicBlock, Value>)
    : Instruction(name, type, incomings.values.toTypedArray()) {
    private val predecessors = incomings.keys.toTypedArray()

    fun getPredecessors() = predecessors
    fun getIncomingValues() = operands
    fun getIncomings() = predecessors.zip(operands).toMap()

    override fun print(): String {
        val sb = StringBuilder()
        sb.appendln("$name = phi {")
        for (indx in 0 until predecessors.size)
            sb.appendln("\t ${predecessors[indx].name} -> ${operands[indx]}")
        sb.append("\t}")
        return sb.toString()
    }
}