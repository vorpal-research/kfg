package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.UsageContext
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type

class ReturnInst : TerminateInst {
    internal constructor(type: Type, ctx: UsageContext) : super(UndefinedName(), type, arrayOf(), arrayOf(), ctx)
    internal constructor(retval: Value, ctx: UsageContext) : super(
        UndefinedName(),
        retval.type,
        arrayOf(retval),
        arrayOf(),
        ctx
    )

    val hasReturnValue: Boolean
        get() = ops.isNotEmpty()

    val returnType: Type
        get() = ops[0].type

    val returnValue: Value
        get() = ops[0]

    override val isTerminate get() = true
    override fun print(): String {
        val sb = StringBuilder()
        sb.append("return")
        if (hasReturnValue) sb.append(" $returnValue")
        return sb.toString()
    }

    override fun clone(ctx: UsageContext): Instruction = when {
        hasReturnValue -> ReturnInst(returnValue, ctx)
        else -> ReturnInst(type, ctx)
    }
}