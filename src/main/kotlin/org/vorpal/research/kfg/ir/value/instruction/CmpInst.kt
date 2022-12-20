package org.vorpal.research.kfg.ir.value.instruction

import org.vorpal.research.kfg.ir.value.Name
import org.vorpal.research.kfg.ir.value.UsageContext
import org.vorpal.research.kfg.ir.value.Value
import org.vorpal.research.kfg.type.Type

class CmpInst internal constructor(
    name: Name,
    type: Type,
    val opcode: CmpOpcode,
    lhv: Value,
    rhv: Value,
    ctx: UsageContext
) : Instruction(name, type, mutableListOf(lhv, rhv), ctx) {

    val lhv: Value
        get() = ops[0]

    val rhv: Value
        get() = ops[1]

    override fun print() = "$name = ($lhv $opcode $rhv)"
    override fun clone(ctx: UsageContext): Instruction = CmpInst(name.clone(), type, opcode, lhv, rhv, ctx)
}
