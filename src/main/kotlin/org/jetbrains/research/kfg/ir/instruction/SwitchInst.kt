package org.jetbrains.research.kfg.ir.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.value.Value

class SwitchInst(key: Value, val default: BasicBlock, val branches: Map<Value, BasicBlock>) : Instruction(arrayOf(key)) {
    override fun print(): String {
        val sb = StringBuilder()
        sb.appendln("switch (${operands[0].getName()}) {")
        for (it in branches)
            sb.appendln("\t ${it.key.getName()} -> ${it.value.name}")
        sb.appendln("\t else -> ${default.name}")
        sb.append("\t}")
        return sb.toString()
    }
}