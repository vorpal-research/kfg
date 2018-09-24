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

object RetvalBuilder : MethodVisitor {
    private val retvals = hashMapOf<BasicBlock, ReturnInst>()

    override fun cleanup() {
        retvals.clear()
    }

    override fun visitReturnInst(inst: ReturnInst) {
        val bb = inst.parent ?: throw InvalidStateError("Method instruction does not have parent")
        retvals[bb] = inst
    }

    override fun visit(method: Method) {
        super.visit(method)
        if (retvals.size <= 1) return


        val returnBlock = BodyBlock("bb.return")

        val incomings = hashMapOf<BasicBlock, Value>()
        for ((bb, `return`) in retvals) {
            bb.remove(`return`)
            bb.addSuccessor(returnBlock)
            returnBlock.addPredecessor(bb)
            if (`return`.hasReturnValue) incomings[bb] = `return`.returnValue

            val jump = IF.getJump(returnBlock)
            jump.location = `return`.location
            bb += jump
        }

        val insts = arrayListOf<Instruction>()
        val `return` = when {
            method.returnType.isVoid -> IF.getReturn()
            else -> {
                val type = mergeTypes(incomings.values.map { it.type }.toSet())
                        ?: method.returnType

                val retval = IF.getPhi("retval", type, incomings)
                insts.add(retval)

                val returnValue = when {
                    type == method.returnType -> retval
                    else -> {
                        val retvalCasted = IF.getCast("retval.casted", method.returnType, retval)
                        insts.add(retvalCasted)
                        retvalCasted
                    }
                }

                IF.getReturn(returnValue)
            }
        }
        insts.add(`return`)
        returnBlock.addAll(*insts.toTypedArray())
        method.add(returnBlock)
    }
}