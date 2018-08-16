package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.Value

class ExitMonitorInst(owner: Value)
    : Instruction(UndefinedName, TF.voidType, arrayOf(owner)) {

    val owner: Value
        get() = ops[0]

    override fun print() = "exit monitor $owner"
    override fun clone(): Instruction = ExitMonitorInst(owner)
}