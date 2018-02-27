package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.type.TypeFactory

class JumpInst(val successor: BasicBlock)
    : TerminateInst(UndefinedName.instance, TypeFactory.instance.getVoidType(), arrayOf()) {

    override fun isTerminate() = true
    override fun print() = "goto ${successor.name}"
}