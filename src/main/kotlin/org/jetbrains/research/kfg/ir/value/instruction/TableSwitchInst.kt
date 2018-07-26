package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.Value

class TableSwitchInst(index: Value, min: Value, max: Value, default: BasicBlock, branches: Array<BasicBlock>)
    : TerminateInst(UndefinedName, TF.getVoidType(), arrayOf(index, min, max), arrayOf(default, *branches)) {

    val index: Value
        get() = ops[0]

    val min: Value
        get() = ops[1]

    val max: Value
        get() = ops[2]

    fun getDefault() = succs[0]
    fun getBranches() = succs.drop(1)

    override fun print(): String {
        val sb = StringBuilder()
        sb.append("tableswitch ($index) {")
        getBranches().withIndex().forEach { (index, successor) -> sb.append("$index -> ${successor.name}; ") }
        sb.append("else -> ${getDefault().name}}")
        return sb.toString()
    }

    override fun clone(): Instruction = TableSwitchInst(index, min, max, getDefault(), getBranches().toTypedArray())
}