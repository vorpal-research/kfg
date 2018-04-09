package org.jetbrains.research.kfg.builder.cfg

import org.jetbrains.research.kfg.IF
import org.jetbrains.research.kfg.UnexpectedException
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.BodyBlock
import org.jetbrains.research.kfg.ir.CatchBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.ReturnInst
import org.jetbrains.research.kfg.visitor.MethodVisitor

class RetvalBuilder(method: Method) : MethodVisitor(method) {
    val returnBlock = BodyBlock("bb.return", method)
    val retvals = mutableMapOf<BasicBlock, ReturnInst>()

    override fun visitReturnInst(inst: ReturnInst) {
        val bb = inst.parent ?: throw UnexpectedException("Method instruction does not have parent")
        retvals[bb] = inst
    }

    override fun visit() {
        super.visit()
        if (retvals.size == 1) return

        val incomings = mutableMapOf<BasicBlock, Value>()
        for ((bb, `return`) in retvals) {
            bb.remove(`return`)
            bb.addSuccessor(returnBlock)
            returnBlock.addPredecessor(bb)
            if (`return`.hasReturnValue()) incomings[bb] = `return`.getReturnValue()
            bb.addInstruction(IF.getJump(returnBlock))
        }
        if (method.desc.retval.isVoid()) {
            val `return` = IF.getReturn()
            returnBlock.addInstruction(`return`)
        } else {
            val retval = IF.getPhi("retval", method.desc.retval, incomings)
            val `return` = IF.getReturn(retval)
            returnBlock.addInstructions(retval, `return`)
        }
        method.add(returnBlock)
    }
}