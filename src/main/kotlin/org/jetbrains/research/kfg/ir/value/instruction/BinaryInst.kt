package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.Name

class BinaryInst(name: Name, val opcode: BinaryOpcode, lhv: Value, rhv: Value)
    : Instruction(name, lhv.type, arrayOf(lhv, rhv)) {

    val lhv: Value
        get() = ops[0]

    val rhv: Value
        get() = ops[1]

    override fun print()= "$name = $lhv $opcode $rhv"
    override fun clone(): Instruction = BinaryInst(name.clone(), opcode, lhv, rhv)
}