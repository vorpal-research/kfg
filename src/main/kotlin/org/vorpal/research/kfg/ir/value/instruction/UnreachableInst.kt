package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.value.UndefinedName
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.type.Type

class UnreachableInst internal constructor(type: Type, ctx: UsageContext) :
    TerminateInst(UndefinedName(), type, arrayOf(), arrayOf(), ctx) {
    override fun print() = "unreachable"

    override fun clone(ctx: UsageContext) = this
}