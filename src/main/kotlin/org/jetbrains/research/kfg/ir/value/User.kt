package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.type.Type

abstract class User(name: ValueName, type: Type, val operands: Array<Value>) : Value(name, type) {

    init {
        operands.forEach { it.addUser(this) }
    }

    fun replaceUsesOf(from: Value, to: Value) {
        (0 until operands.size)
                .filter { operands[it] == from }
                .forEach {
                    operands[it].removeUser(this)
                    operands[it] = to
                    to.addUser(this)
                }
    }
}