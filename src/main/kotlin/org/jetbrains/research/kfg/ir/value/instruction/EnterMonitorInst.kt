package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type

class EnterMonitorInst(type: Type, owner: Value)
    : Instruction(UndefinedName(), type, arrayOf(owner)) {

    val owner: Value
        get() = ops[0]

    override fun print() = "enter monitor $owner"
    override fun clone(): Instruction = EnterMonitorInst(type, owner)
}