package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.User
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.ValueName

abstract class Instruction(name: ValueName, type: Type, operands: Array<Value>)
    : User(name, type, operands), Iterable<Value> {
    var bb : BasicBlock? = null

    abstract fun print(): String
    open fun isTerminate() = false
    override fun iterator(): Iterator<Value> = operands.iterator()
}

abstract class TerminateInst(name: ValueName, type: Type, operands: Array<Value>): Instruction(name, type, operands) {
    override fun isTerminate() = true
}