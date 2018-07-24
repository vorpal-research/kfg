package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Location
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.type.Type

abstract class Instruction(name: Name, type: Type, protected val ops: Array<Value>)
    : Value(name, type), ValueUser, Iterable<Value> {

    var parent: BasicBlock? = null
    var location = Location()
    open val isTerminate = false

    val operands: List<Value>
        get() = ops.toList()

    init {
        ops.forEach { it.addUser(this) }
    }


    abstract fun print(): String
    override fun iterator(): Iterator<Value> = ops.iterator()

    override fun replaceUsesOf(from: UsableValue, to: UsableValue) {
        (0..ops.lastIndex)
                .filter { ops[it] == from }
                .forEach {
                    ops[it].removeUser(this)
                    ops[it] = to.get()
                    to.addUser(this)
                }
    }

    abstract fun clone(): Instruction
    open fun update(remapping: Map<Value, Value>): Instruction {
        val new = clone()
        remapping.forEach { from, to -> new.replaceUsesOf(from, to) }
        return new
    }
}

abstract class TerminateInst(name: Name, type: Type, operands: Array<Value>, protected val succs: Array<BasicBlock>) :
        Instruction(name, type, operands), BlockUser {

    val successors: List<BasicBlock>
        get() = succs.toList()

    override val isTerminate = true

    init {
        succs.forEach { it.addUser(this) }
    }

    override fun replaceUsesOf(from: UsableBlock, to: UsableBlock) {
        (0..succs.lastIndex)
                .filter { succs[it] == from }
                .forEach {
                    succs[it].removeUser(this)
                    succs[it] = to.get()
                    to.addUser(this)
                }
    }
}