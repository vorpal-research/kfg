package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.value.*
import org.vorpal.research.kfg.type.Type

class PhiInst internal constructor(
    name: Name,
    type: Type,
    predecessors: List<BasicBlock>,
    operands: List<Value>,
    ctx: UsageContext
) : Instruction(name, type, operands.toMutableList(), ctx), BlockUser {
    private val preds = predecessors.toMutableList()

    init {
        with(ctx) {
            incomings.keys.forEach { it.addUser(this@PhiInst) }
        }
    }

    val predecessors: List<BasicBlock>
        get() = preds

    val incomingValues: List<Value>
        get() = ops

    val incomings: Map<BasicBlock, Value>
        get() = predecessors.zip(ops).toMap()

    override fun print() = buildString {
        append("$name = phi {")
        append(predecessors.indices
            .joinToString(separator = "; ") {
                "${predecessors[it].name} -> ${incomingValues[it]}"
            })
        append("}")
    }

    override fun clone(ctx: UsageContext): Instruction = PhiInst(name.clone(), type, predecessors, operands, ctx)

    override fun replaceUsesOf(ctx: BlockUsageContext, from: UsableBlock, to: UsableBlock) = with(ctx) {
        (0..preds.lastIndex)
            .filter { preds[it] == from }
            .forEach {
                preds[it].removeUser(this@PhiInst)
                preds[it] = to.get()
                to.addUser(this@PhiInst)
            }
    }

    fun replaceAllUsesWith(ctx: UsageContext, to: UsableValue) = with(ctx) {
        this@PhiInst.replaceAllUsesWith(to)
        if (to is BlockUser) {
            for (it in preds) {
                it.removeUser(this@PhiInst)
                it.addUser(to)
            }
        }
    }
}
