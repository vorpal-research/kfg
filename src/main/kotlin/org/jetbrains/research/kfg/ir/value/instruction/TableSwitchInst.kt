package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.Value

class TableSwitchInst(index: Value, min: Value, max: Value, default: BasicBlock, branches: Array<BasicBlock>)
    : TerminateInst(UndefinedName, TF.getVoidType(), arrayOf(index, min, max), arrayOf(default, *branches)) {

    fun getIndex() = operands[0]
    fun getMin() = operands[1]
    fun getMax() = operands[2]

    fun getDefault() = successors[0]
    fun getBranches() = successors.drop(1)

    override fun print(): String {
        val sb = StringBuilder()
        sb.append("tableswitch (${getIndex()}) {")
        getBranches().withIndex().forEach { (index, successor) -> sb.append("$index -> ${successor.name}; ") }
        sb.append("else -> ${getDefault().name}}")
        return sb.toString()
    }

    override fun clone(): Instruction = TableSwitchInst(getIndex(), getMin(), getMax(), getDefault(), getBranches().toTypedArray())
}