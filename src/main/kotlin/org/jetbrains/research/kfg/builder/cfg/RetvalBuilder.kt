package org.jetbrains.research.kfg.builder.cfg

import org.jetbrains.research.kfg.IF
import org.jetbrains.research.kfg.InvalidStateError
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.BodyBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.ir.value.instruction.ReturnInst
import org.jetbrains.research.kfg.type.mergeTypes
import org.jetbrains.research.kfg.visitor.MethodVisitor

class RetvalBuilder(method: Method) : MethodVisitor(method) {
    val returnBlock = BodyBlock("bb.return")
    val retvals = hashMapOf<BasicBlock, ReturnInst>()

    override fun visitReturnInst(inst: ReturnInst) {
        val bb = inst.parent ?: throw InvalidStateError("Method instruction does not have parent")
        retvals[bb] = inst
    }

    override fun visit() {
        super.visit()
        if (retvals.size <= 1) return

        val incomings = hashMapOf<BasicBlock, Value>()
        for ((bb, `return`) in retvals) {
            bb.remove(`return`)
            bb.addSuccessor(returnBlock)
            returnBlock.addPredecessor(bb)
            if (`return`.hasReturnValue) incomings[bb] = `return`.returnValue

            val jump = IF.getJump(returnBlock)
            jump.location = `return`.location
            bb.addInstruction(jump)
        }

        val insts = arrayListOf<Instruction>()
        val `return` = when {
            method.desc.retval.isVoid() -> IF.getReturn()
            else -> {
                val type = mergeTypes(incomings.values.map { it.type }.toSet())
                        ?: throw InvalidStateError("Can't merge incomings of return type")

                val retval = IF.getPhi("retval", type, incomings)
                insts.add(retval)

                val returnValue = when {
                    type == method.desc.retval -> retval
                    else -> {
                        val retvalCasted = IF.getCast("retval.casted", method.desc.retval, retval)
                        insts.add(retvalCasted)
                        retvalCasted
                    }
                }

                IF.getReturn(returnValue)
            }
        }
        insts.add(`return`)
        returnBlock.addInstructions(*insts.toTypedArray())
        method.add(returnBlock)
    }
}