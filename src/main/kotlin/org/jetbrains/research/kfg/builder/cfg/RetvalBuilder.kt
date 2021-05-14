package org.jetbrains.research.kfg.builder.cfg

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
import org.jetbrains.research.kthelper.assert.ktassert
import org.jetbrains.research.kthelper.logging.log
import kotlin.math.abs

class RetvalBuilder(override val cm: ClassManager) : MethodVisitor {
    private val returnValues = hashMapOf<BasicBlock, ReturnInst>()

    override fun cleanup() {
        returnValues.clear()
    }

    override fun visitReturnInst(inst: ReturnInst) {
        val bb = inst.parent
        returnValues[bb] = inst
    }

    override fun visit(method: Method) {
        super.visit(method)
        if (returnValues.size <= 1) return


        val returnBlock = BodyBlock("bb.return")

        val incomings = hashMapOf<BasicBlock, Value>()
        for ((bb, returnInst) in returnValues) {
            bb.remove(returnInst)
            bb.addSuccessor(returnBlock)
            returnBlock.addPredecessor(bb)
            if (returnInst.hasReturnValue)
                incomings[bb] = returnInst.returnValue

            val jump = instructions.getJump(returnBlock)
            jump.location = returnInst.location
            bb += jump
        }

        val instructions = arrayListOf<Instruction>()
        val returnInstruction = when {
            method.returnType.isVoid -> this.instructions.getReturn()
            else -> {
                val type = mergeTypes(types, incomings.values.map { it.type }.toSet()) ?: method.returnType

                val retval = this.instructions.getPhi("retval", type, incomings)
                instructions.add(retval)

                val returnValue = when (type) {
                    method.returnType -> retval
                    is Integral -> {
                        val methodRetType = method.returnType
                        ktassert(methodRetType is Integral) {
                            log.error("Return value type is integral and method return type is ${method.returnType}")
                        }

                        // if return type is Int and return value type is Long (or vice versa), we need casting
                        // otherwise it's fine
                        if (abs(type.bitSize - methodRetType.bitSize) >= Type.WORD) {
                            val retvalCasted = this.instructions.getCast("retval.casted", method.returnType, retval)
                            instructions.add(retvalCasted)
                            retvalCasted
                        } else {
                            retval
                        }
                    }
                    else -> {
                        val retvalCasted = this.instructions.getCast("retval.casted", method.returnType, retval)
                        instructions.add(retvalCasted)
                        retvalCasted
                    }
                }

                this.instructions.getReturn(returnValue)
            }
        }
        instructions.add(returnInstruction)
        returnBlock.addAll(*instructions.toTypedArray())
        method.add(returnBlock)
    }
}