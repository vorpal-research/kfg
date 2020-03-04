package org.jetbrains.research.kfg.builder.cfg

import com.abdullin.kthelper.assert.ktassert
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.BodyBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.ir.value.instruction.ReturnInst
import org.jetbrains.research.kfg.type.Integral
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.mergeTypes
import org.jetbrains.research.kfg.visitor.MethodVisitor
import kotlin.math.abs

class RetvalBuilder(override val cm: ClassManager) : MethodVisitor {
    private val retvals = hashMapOf<BasicBlock, ReturnInst>()

    override fun cleanup() {
        retvals.clear()
    }

    override fun visitReturnInst(inst: ReturnInst) {
        val bb = inst.parent
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

            val jump = instructions.getJump(returnBlock)
            jump.location = `return`.location
            bb += jump
        }

        val insts = arrayListOf<Instruction>()
        val `return` = when {
            method.returnType.isVoid -> instructions.getReturn()
            else -> {
                val type = mergeTypes(types, incomings.values.map { it.type }.toSet()) ?: method.returnType

                val retval = instructions.getPhi("retval", type, incomings)
                insts.add(retval)

                val returnValue = when (type) {
                    method.returnType -> retval
                    is Integral -> {
                        val methodRetType = method.returnType
                        ktassert(methodRetType is Integral) { log.error("Return value type is integral and method return type is ${method.returnType}") }

                        // if return type is Int and return value type is Long (or vice versa), we need casting
                        // otherwise it's fine
                        if (abs(type.bitsize - methodRetType.bitsize) >= Type.WORD) {
                            val retvalCasted = instructions.getCast("retval.casted", method.returnType, retval)
                            insts.add(retvalCasted)
                            retvalCasted
                        } else {
                            retval
                        }
                    }
                    else -> {
                        val retvalCasted = instructions.getCast("retval.casted", method.returnType, retval)
                        insts.add(retvalCasted)
                        retvalCasted
                    }
                }

                instructions.getReturn(returnValue)
            }
        }
        insts.add(`return`)
        returnBlock.addAll(*insts.toTypedArray())
        method.add(returnBlock)
    }
}