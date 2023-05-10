package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.value.UndefinedName
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.type.Type

@Suppress("MemberVisibilityCanBePrivate")
class ThrowInst internal constructor(type: Type, exc: Value, ctx: UsageContext) :
    TerminateInst(UndefinedName(), type, mutableListOf(exc), mutableListOf(), ctx) {

    val throwable: Value
        get() = ops[0]

    override fun print() = "throw $throwable"
    override fun clone(ctx: UsageContext): Instruction = ThrowInst(type, throwable, ctx)
}
