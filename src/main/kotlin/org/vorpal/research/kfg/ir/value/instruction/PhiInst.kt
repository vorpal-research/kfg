package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.value.*
import org.vorpal.research.kfg.type.Type

class PhiInst internal constructor(name: Name, type: Type, incomings: Map<BasicBlock, Value>, ctx: UsageContext) :
    Instruction(name, type, incomings.values.toTypedArray(), ctx), BlockUser {
    private val preds = incomings.keys.toTypedArray()

    init {
        with(ctx) {
            incomings.keys.forEach { it.addUser(this@PhiInst) }
        }
    }

    val predecessors: List<BasicBlock>
        get() = preds.toList()

    val incomingValues: List<Value>
        get() = ops.toList()

    val incomings: Map<BasicBlock, Value>
        get() = predecessors.zip(ops).toMap()

    override fun print() = buildString {
        append("$name = phi {")
        append(incomings.toList()
            .joinToString(separator = "; ") {
                "${it.first.name} -> ${it.second}"
            })
        append("}")
    }

    override fun clone(ctx: UsageContext): Instruction = PhiInst(name.clone(), type, incomings, ctx)

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