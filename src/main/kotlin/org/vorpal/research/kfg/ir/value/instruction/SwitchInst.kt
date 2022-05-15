package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.value.UndefinedName
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.type.Type

class SwitchInst internal constructor(
    key: Value,
    type: Type,
    default: BasicBlock,
    branches: Map<Value, BasicBlock>,
    ctx: UsageContext
) : TerminateInst(
    UndefinedName(),
    type,
    arrayOf(key, *branches.keys.toTypedArray()),
    arrayOf(default, *branches.values.toTypedArray()),
    ctx
) {

    val key: Value
        get() = ops[0]

    val default: BasicBlock
        get() = succs[0]

    val branches: Map<Value, BasicBlock>
        get() = ops.drop(1).zip(succs.drop(1)).toMap()

    override fun print(): String {
        val sb = StringBuilder()
        sb.append("switch ($key) {")
        branches.forEach { sb.append("${it.key} -> ${it.value.name}; ") }
        sb.append("else -> ${default.name}}")
        return sb.toString()
    }

    override fun clone(ctx: UsageContext): Instruction = SwitchInst(key, type, default, branches, ctx)
}