package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.Value

class SwitchInst(key: Value, default: BasicBlock, branches: Map<Value, BasicBlock>) :
        TerminateInst(
                UndefinedName,
                TF.voidType,
                arrayOf(key, *branches.keys.toTypedArray()),
                arrayOf(default, *branches.values.toTypedArray())) {

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

    override fun clone(): Instruction = SwitchInst(key, default, branches)
}