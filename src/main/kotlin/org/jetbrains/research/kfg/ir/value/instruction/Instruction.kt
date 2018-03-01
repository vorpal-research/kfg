package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.User
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.ValueName

abstract class Instruction(name: ValueName, type: Type, val operands: Array<Value>)
    : Value(name, type), User<Value>, Iterable<Value> {
    var parent : BasicBlock? = null

    init {
        operands.forEach { it.addUser(this) }
    }

    abstract fun print(): String
    open fun isTerminate() = false
    override fun iterator(): Iterator<Value> = operands.iterator()

    override fun replaceUsesOf(from: Value, to: Value) {
        (0 until operands.size)
                .filter { operands[it] == from }
                .forEach {
                    operands[it].removeUser(this)
                    operands[it] = to
                    to.addUser(this)
                }
    }
}

abstract class TerminateInst(name: ValueName, type: Type, operands: Array<Value>): Instruction(name, type, operands) {
    override fun isTerminate() = true
}