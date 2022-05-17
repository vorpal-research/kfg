package org.vorpal.research.kfg.builder.cfg

import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.BodyBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.ir.value.ValueFactory
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.InstructionBuilder
import org.vorpal.research.kfg.ir.value.instruction.InstructionFactory
import org.vorpal.research.kfg.ir.value.instruction.ReturnInst
import org.vorpal.research.kfg.type.Integral
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kfg.type.mergeTypes
import org.vorpal.research.kfg.visitor.MethodVisitor
import org.vorpal.research.kfg.visitor.PipelineStub
import org.vorpal.research.kthelper.assert.ktassert
import kotlin.math.abs

class RetvalBuilder(override val cm: ClassManager, override val ctx: UsageContext) : MethodVisitor, InstructionBuilder {
    override val pipeline = PipelineStub()
    private val returnValues = hashMapOf<BasicBlock, ReturnInst>()
    override val instructions: InstructionFactory
        get() = cm.instruction
    override val types: TypeFactory
        get() = cm.type
    override val values: ValueFactory
        get() = cm.value

    override fun cleanup() {
        returnValues.clear()
    }

    override fun visitReturnInst(inst: ReturnInst) {
        val bb = inst.parent
        returnValues[bb] = inst
    }

    override fun visit(method: Method) = with(ctx) {
        super.visit(method)
        if (returnValues.size <= 1) return


        val returnBlock = BodyBlock("bb.return")

        val incomings = hashMapOf<BasicBlock, Value>()
        for ((bb, returnInst) in returnValues) {
            bb.remove(returnInst)
            returnInst.clearUses()
            bb.linkForward(returnBlock)
            if (returnInst.hasReturnValue)
                incomings[bb] = returnInst.returnValue

            val jump = goto(returnBlock)
            jump.location = returnInst.location
            bb += jump
        }

        val instructions = arrayListOf<Instruction>()
        val returnInstruction = when {
            method.returnType.isVoid -> `return`()
            else -> {
                val type = mergeTypes(types, incomings.values.map { it.type }.toSet()) ?: method.returnType

                val retval = phi("retval", type, incomings)
                instructions.add(retval)

                val returnValue = when (type) {
                    method.returnType -> retval
                    is Integral -> {
                        val methodRetType = method.returnType
                        ktassert(methodRetType is Integral, "Return value type is integral and method return type is ${method.returnType}")

                        // if return type is Int and return value type is Long (or vice versa), we need casting
                        // otherwise it's fine
                        if (abs(type.bitSize - methodRetType.bitSize) >= Type.WORD) {
                            val retvalCasted = retval.cast("retval.casted", method.returnType)
                            instructions.add(retvalCasted)
                            retvalCasted
                        } else {
                            retval
                        }
                    }
                    else -> {
                        val retvalCasted = retval.cast("retval.casted", method.returnType)
                        instructions.add(retvalCasted)
                        retvalCasted
                    }
                }

                `return`(returnValue)
            }
        }
        instructions.add(returnInstruction)
        returnBlock.addAll(*instructions.toTypedArray())
        method.add(returnBlock)
    }
}