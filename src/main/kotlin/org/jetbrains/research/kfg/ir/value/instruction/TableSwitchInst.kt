package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.Value

class TableSwitchInst(index: Value, min: Value, max: Value, val default: BasicBlock, val branches: Array<BasicBlock>)
    : TerminateInst(UndefinedName.instance, TF.getVoidType(), arrayOf(index, min, max)) {

    fun getIndex() = operands[0]
    fun getMin() = operands[1]
    fun getMax() = operands[2]

    override fun print(): String {
        val sb = StringBuilder()
        sb.appendln("tableswitch (${getIndex()}) {}")
        return sb.toString()
    }
}