package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.Name
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.NullType

class ArrayLoadInst(name: Name, arrayRef: Value, index: Value)
    : Instruction(name, when {
        arrayRef.type === NullType -> TF.getNullType()
        else -> (arrayRef.type as ArrayType).component
    }, arrayOf(arrayRef, index)) {

    val arrayRef get() = ops[0]
    val index get() = ops[1]

    override fun print() = "$name = $arrayRef[$index]"
    override fun clone(): Instruction = ArrayLoadInst(name.clone(), arrayRef, index)
}