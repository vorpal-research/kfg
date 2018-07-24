package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.Name
import org.jetbrains.research.kfg.type.Type

class InstanceOfInst(name: Name, val targetType: Type, obj: Value)
    : Instruction(name, TF.getIntType(), arrayOf(obj)) {

    val operand get() = ops[0]

    override fun print() = "$name = $operand instanceOf ${targetType.name}"
    override fun clone(): Instruction = InstanceOfInst(name.clone(), targetType, operand)
}