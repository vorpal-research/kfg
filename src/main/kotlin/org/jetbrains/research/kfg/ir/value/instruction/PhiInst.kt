package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.BlockUser
import org.jetbrains.research.kfg.ir.value.Name
import org.jetbrains.research.kfg.ir.value.UsableBlock
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type

class PhiInst(name: Name, type: Type, incomings: Map<BasicBlock, Value>)
    : Instruction(name, type, incomings.values.toTypedArray()), BlockUser {
    private val preds = incomings.keys.toTypedArray()

    val predecessors: List<BasicBlock>
        get() = preds.toList()

    val incomingValues: List<Value>
        get() = ops.toList()

    val incomings: Map<BasicBlock, Value>
        get() = predecessors.zip(ops).toMap()

    override fun print(): String {
        val sb = StringBuilder()
        sb.append("$name = phi {")
        sb.append(incomings.toList().joinToString(separator = "; ") { "${it.first.name} -> ${it.second}" })
        sb.append("}")
        return sb.toString()
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
}