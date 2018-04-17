package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.type.Type

abstract class Instruction(name: Name, type: Type, protected val operands: Array<Value>)
    : Value(name, type), ValueUser, Iterable<Value> {
    var parent: BasicBlock? = null
        internal set

    init {
        operands.forEach { it.addUser(this) }
    }

    fun operands() = operands.clone()

    abstract fun print(): String
    open fun isTerminate() = false
    override fun iterator(): Iterator<Value> = operands.iterator()

    override fun replaceUsesOf(from: UsableValue, to: UsableValue) {
        (0 until operands.size)
                .filter { operands[it] == from }
                .forEach {
                    operands[it].removeUser(this)
                    operands[it] = to.get()
                    to.addUser(this)
                }
    }
}

abstract class TerminateInst(name: Name, type: Type, operands: Array<Value>, protected val successors: Array<BasicBlock>) :
        Instruction(name, type, operands), BlockUser {
    init {
        successors.forEach { it.addUser(this) }
    }

    fun successors() = successors.clone()
    override fun isTerminate() = true

    override fun replaceUsesOf(from: UsableBlock, to: UsableBlock) {
        (0 until successors.size)
                .filter { successors[it] == from }
                .forEach {
                    successors[it].removeUser(this)
                    successors[it] = to.get()
                    to.addUser(this)
                }
    }
}