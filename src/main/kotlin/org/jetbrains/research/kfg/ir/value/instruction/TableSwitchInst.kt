package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type

class TableSwitchInst(type: Type, index: Value, min: Value, max: Value, default: BasicBlock, branches: Array<BasicBlock>)
    : TerminateInst(UndefinedName, type, arrayOf(index, min, max), arrayOf(default, *branches)) {

    val index: Value
        get() = ops[0]

    val min: Value
        get() = ops[1]

    val max: Value
        get() = ops[2]

    val default get() = succs[0]
    val branches get() = succs.drop(1)

    override fun print() = buildString {
        append("tableswitch ($index) {")
        for ((index, successor) in branches.withIndex()) {
            append("$index -> ${successor.name}; ")
        }
        append("else -> ${default.name}}")
    }

    override fun clone(): Instruction = TableSwitchInst(type, index, min, max, default, branches.toTypedArray())
}