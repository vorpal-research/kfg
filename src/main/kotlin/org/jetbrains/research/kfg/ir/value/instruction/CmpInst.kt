package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.Name

class CmpInst(name: Name, type: Type, val opcode: CmpOpcode, lhv: Value, rhv: Value)
    : Instruction(name, type, arrayOf(lhv, rhv)) {

    val lhv get() = ops[0]
    val rhv get() = ops[1]

    override fun print() = "$name = ($lhv $opcode $rhv)"
    override fun clone(): Instruction = CmpInst(name.clone(), type, opcode, lhv, rhv)
}