package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.type.Type

class PhiInst(name: Name, type: Type, incomings: Map<BasicBlock, Value>)
    : Instruction(name, type, incomings.values.toTypedArray()), BlockUser {
    private val preds = incomings.keys.toTypedArray()

    init {
        incomings.keys.forEach { it.addUser(this) }
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

    override fun clone(): Instruction = PhiInst(name.clone(), type, incomings)

    override fun replaceUsesOf(from: UsableBlock, to: UsableBlock) {
        (0..preds.lastIndex)
                .filter { preds[it] == from }
                .forEach {
                    preds[it].removeUser(this)
                    preds[it] = to.get()
                    to.addUser(this)
                }
    }

    override fun replaceAllUsesWith(to: UsableValue) {
        super.replaceAllUsesWith(to)
        if (to is BlockUser) {
            for (it in preds) {
                it.removeUser(this)
                it.addUser(to)
            }
        }
    }
}