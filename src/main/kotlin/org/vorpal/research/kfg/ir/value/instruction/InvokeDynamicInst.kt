package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.MethodDesc
import org.vorpal.research.kfg.ir.value.Name
import org.vorpal.research.kfg.ir.value.Slot
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.Value

data class Handle(val tag: Int, val method: Method, val isInterface: Boolean) {
    override fun toString() = "@$method"
}

class InvokeDynamicInst(
    name: Name,
    val methodName: String,
    val methodDesc: MethodDesc,
    val bootstrapMethod: Handle,
    val bootstrapMethodArgs: Array<Any>,
    operands: Array<Value>,
    ctx: UsageContext
) : Instruction(name, methodDesc.returnType, operands, ctx) {
    val args: List<Value> get() = ops.toList()

    constructor(
        methodName: String,
        methodDesc: MethodDesc,
        bootstrapMethod: Handle,
        bootstrapMethodArgs: Array<Any>,
        operands: Array<Value>,
        ctx: UsageContext
    ) : this(Slot(), methodName, methodDesc, bootstrapMethod, bootstrapMethodArgs, operands, ctx)

    override fun print(): String = buildString {
        append("$name = invokeDynamic $methodDesc $bootstrapMethod(${bootstrapMethodArgs.joinToString(", ")})")
    }

    override fun clone(ctx: UsageContext): Instruction =
        InvokeDynamicInst(methodName, methodDesc, bootstrapMethod, bootstrapMethodArgs, args.toTypedArray(), ctx)
}