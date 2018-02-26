package org.jetbrains.research.kfg.ir.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.value.Value

class PhiInst(lhv: Value, val incomings: Map<BasicBlock, Value>): Instruction(arrayOf(lhv)) {
    fun getLhv() = operands[0]
    override fun print(): String {
        val sb = StringBuilder()
        sb.appendln("${getLhv().getName()} = phi [")
        for (pr in incomings)
            sb.appendln("\t ${pr.key.name} -> ${pr.value.getName()}")
        sb.append("\t]")
        return sb.toString()
    }
}