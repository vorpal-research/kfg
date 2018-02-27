package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.ir.value.Value

class PhiInst(name: String, type: Type, val incomings: Map<BasicBlock, Value>)
    : Instruction(name, type, incomings.values.toTypedArray()) {
    override fun print(): String {
        val sb = StringBuilder()
        sb.appendln("$name = phi [")
        for (pr in incomings)
            sb.appendln("\t ${pr.key.name} -> ${pr.value}")
        sb.append("\t]")
        return sb.toString()
    }
}