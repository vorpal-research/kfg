package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.value.IntConstant
import org.vorpal.research.kfg.ir.value.UndefinedName
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.type.Type

class TableSwitchInst internal constructor(
    type: Type,
    index: Value,
    min: Value,
    max: Value,
    default: BasicBlock,
    branches: List<BasicBlock>,
    ctx: UsageContext
) : TerminateInst(
    UndefinedName(),
    type,
    mutableListOf(index, min, max),
    mutableListOf(default).also { it.addAll(branches) },
    ctx
) {

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
        TableSwitchInst(type, index, min, max, default, branches, ctx)
}
