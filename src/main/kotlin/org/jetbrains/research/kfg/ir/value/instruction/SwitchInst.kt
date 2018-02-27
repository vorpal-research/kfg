package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.ir.value.Value

class SwitchInst(key: Value, val default: BasicBlock, val branches: Map<Value, BasicBlock>)
    : Instruction("", TypeFactory.instance.getVoidType(), arrayOf(key)) {

    fun getKey() = operands[0]

    override fun print(): String {
        val sb = StringBuilder()
        sb.appendln("switch (${getKey()}) {")
        for (it in branches)
            sb.appendln("\t ${it.key.name} -> ${it.value.name}")
        sb.appendln("\t else -> ${default.name}")
        sb.append("\t}")
        return sb.toString()
    }
}