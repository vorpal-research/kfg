package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.Value

class BranchInst(cond: Value, trueSuccessor: BasicBlock, falseSuccessor: BasicBlock)
    : TerminateInst(UndefinedName, TF.voidType, arrayOf(cond), arrayOf(trueSuccessor, falseSuccessor)) {

    val cond: Value
        get() = ops[0]

    val trueSuccessor: BasicBlock
        get() = succs[0]

    val falseSuccessor: BasicBlock
        get() = succs[1]

    override fun print() = "if ($cond) goto ${trueSuccessor.name} else ${falseSuccessor.name}"
    override fun clone(): Instruction = BranchInst(cond, trueSuccessor, falseSuccessor)
}