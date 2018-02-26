package org.jetbrains.research.kfg.ir.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.value.Value

class TableSwitchInst(index: Value, min: Value, max: Value, val default: BasicBlock, val branches: Array<BasicBlock>)
    : Instruction(arrayOf(index, min, max)) {
    override fun print(): String {
        val sb = StringBuilder()
        sb.appendln("tableswitch (${operands[0].getName()}) {}")
        return sb.toString()
    }
}