package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.ir.value.Value

abstract class Instruction (name: String, type: Type, val operands: Array<Value>): Value(name, type) {
    var bb : BasicBlock? = null

    abstract fun print(): String
    open fun isTerminate() = false
}