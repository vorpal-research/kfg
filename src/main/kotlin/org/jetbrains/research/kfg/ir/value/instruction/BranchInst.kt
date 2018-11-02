package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type

class BranchInst(cond: Value, type: Type, trueSuccessor: BasicBlock, falseSuccessor: BasicBlock)
    : TerminateInst(UndefinedName, type, arrayOf(cond), arrayOf(trueSuccessor, falseSuccessor)) {

    val cond: Value
        get() = ops[0]

    val trueSuccessor: BasicBlock
        get() = succs[0]

    val falseSuccessor: BasicBlock
        get() = succs[1]

    override fun print() = "if ($cond) goto ${trueSuccessor.name} else ${falseSuccessor.name}"
    override fun clone(): Instruction = BranchInst(cond, type, trueSuccessor, falseSuccessor)
}