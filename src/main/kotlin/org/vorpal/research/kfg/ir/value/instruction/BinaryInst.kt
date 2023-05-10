package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.value.Name
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.Value

@Suppress("MemberVisibilityCanBePrivate")
class BinaryInst internal constructor(
    name: Name,
    val opcode: BinaryOpcode,
    lhv: Value,
    rhv: Value,
    ctx: UsageContext
) : Instruction(name, lhv.type, mutableListOf(lhv, rhv), ctx) {

    val lhv: Value
        get() = ops[0]

    val rhv: Value
        get() = ops[1]

    override fun print() = "$name = $lhv $opcode $rhv"
    override fun clone(ctx: UsageContext): Instruction = BinaryInst(name.clone(), opcode, lhv, rhv, ctx)
}
