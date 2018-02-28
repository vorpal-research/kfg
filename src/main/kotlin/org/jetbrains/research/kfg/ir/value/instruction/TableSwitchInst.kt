package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.ir.value.Value

class TableSwitchInst(index: Value, min: Value, max: Value, val default: BasicBlock, val branches: Array<BasicBlock>)
    : TerminateInst(UndefinedName.instance, TypeFactory.instance.getVoidType(), arrayOf(index, min, max)) {
    override fun print(): String {
        val sb = StringBuilder()
        sb.appendln("tableswitch (${operands[0].name}) {}")
        return sb.toString()
    }
}