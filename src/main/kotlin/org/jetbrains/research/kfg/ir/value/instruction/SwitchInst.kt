package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.Value

class SwitchInst(key: Value, val default: BasicBlock, val branches: Map<Value, BasicBlock>)
    : TerminateInst(UndefinedName, TF.getVoidType(), arrayOf(key)) {

    fun getKey() = operands[0]

    override fun print(): String {
        val sb = StringBuilder()
        sb.append("switch (${getKey()}) {")
        branches.forEach { sb.append("${it.key} -> ${it.value.name}; ") }
        sb.append("else -> ${default.name}}")
        return sb.toString()
    }
}