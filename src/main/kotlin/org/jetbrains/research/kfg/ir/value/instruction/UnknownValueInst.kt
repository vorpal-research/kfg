package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.value.Name
import org.jetbrains.research.kfg.ir.value.UsageContext
import org.jetbrains.research.kfg.type.Type

class UnknownValueInst internal constructor(name: Name, type: Type, val ctx: UsageContext) : Instruction(name, type, arrayOf(), ctx) {
    override fun clone(ctx: UsageContext): Instruction = UnknownValueInst(name.clone(), type, ctx)

    override fun print(): String = "$name = unknown<$type>"
}