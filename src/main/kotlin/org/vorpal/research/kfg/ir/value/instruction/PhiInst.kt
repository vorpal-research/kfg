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
    private val predecessorBlocks = predecessors.toMutableList()

    init {
        with(ctx) {
            incomings.keys.forEach { it.addUser(this@PhiInst) }
        }
    }

    val predecessors: List<BasicBlock>
        get() = predecessorBlocks

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
        for (index in predecessorBlocks.indices) {
            if (predecessorBlocks[index] == from) {
                predecessorBlocks[index].removeUser(this@PhiInst)
                predecessorBlocks[index] = to.get()
                to.addUser(this@PhiInst)
            }
        }
    }

    @Suppress("unused")
    fun replaceAllUsesWith(ctx: UsageContext, to: UsableValue) = with(ctx) {
        this@PhiInst.replaceAllUsesWith(to)
        if (to is BlockUser) {
            for (it in predecessorBlocks) {
                it.removeUser(this@PhiInst)
                it.addUser(to)
            }
        }
    }

    override fun clearBlockUses(ctx: BlockUsageContext) = with(ctx) {
        predecessors.forEach {
            it.removeUser(this@PhiInst)
        }
    }
}
