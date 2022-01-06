package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.value.instruction.Instruction

class UnknownValueInst internal constructor(ctx: UsageContext, name: Name, val cm: ClassManager) : Instruction(name, cm.type.intType, arrayOf(), ctx) {
    override fun clone(ctx: UsageContext): Instruction = UnknownValueInst(ctx, name.clone(), cm)

    override fun print(): String = "$name = ???"
}