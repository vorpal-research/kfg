package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.MethodDescriptor
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
    val methodDescriptor: MethodDescriptor,
    val bootstrapMethod: Handle,
    val bootstrapMethodArgs: Array<Any>,
    operands: Array<Value>,
    ctx: UsageContext
) : Instruction(name, methodDescriptor.returnType, operands, ctx) {
    val args: List<Value> get() = ops.toList()

    constructor(
        methodName: String,
        methodDescriptor: MethodDescriptor,
        bootstrapMethod: Handle,
        bootstrapMethodArgs: Array<Any>,
        operands: Array<Value>,
        ctx: UsageContext
    ) : this(Slot(), methodName, methodDescriptor, bootstrapMethod, bootstrapMethodArgs, operands, ctx)

    override fun print(): String = buildString {
        append("$name = invokeDynamic $methodDescriptor $bootstrapMethod(${bootstrapMethodArgs.joinToString(", ")})")
    }

    override fun clone(ctx: UsageContext): Instruction =
        InvokeDynamicInst(methodName, methodDescriptor, bootstrapMethod, bootstrapMethodArgs, args.toTypedArray(), ctx)
}