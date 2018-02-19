package org.jetbrains.research.kfg.ir.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.value.Value

class SwitchInst(val default: BasicBlock, val branches: Map<Value, BasicBlock>) : Instruction(arrayOf()) {
    override fun print() = "switch"
}