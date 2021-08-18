package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.IntConstant
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.UsageContext
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type

class TableSwitchInst internal constructor(
    type: Type,
    index: Value,
    min: Value,
    max: Value,
    default: BasicBlock,
    branches: Array<BasicBlock>,
    ctx: UsageContext
) : TerminateInst(UndefinedName(), type, arrayOf(index, min, max), arrayOf(default, *branches), ctx) {

    val index: Value
        get() = ops[0]

    val min: Value
        get() = ops[1]

    val max: Value
        get() = ops[2]

    val default get() = succs[0]
    val branches get() = succs.drop(1)

    val range: IntRange
        get() = (min as IntConstant).value..(max as IntConstant).value

    override fun print() = buildString {
        append("tableswitch ($index) {")
        for ((index, successor) in branches.withIndex()) {
            append("$index -> ${successor.name}; ")
        }
        append("else -> ${default.name}}")
    }

    override fun clone(ctx: UsageContext): Instruction =
        TableSwitchInst(type, index, min, max, default, branches.toTypedArray(), ctx)
}