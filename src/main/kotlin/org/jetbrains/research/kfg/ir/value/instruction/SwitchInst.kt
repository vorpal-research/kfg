package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.Value

class SwitchInst(key: Value, default: BasicBlock, branches: Map<Value, BasicBlock>)
    : TerminateInst(UndefinedName, TF.getVoidType(), arrayOf(key, *branches.keys.toTypedArray()), arrayOf(default, *branches.values.toTypedArray())) {

    fun getKey() = operands[0]
    fun getDefault() = successors[0]
    fun getBranches() = operands.drop(1).zip(successors.drop(1)).toMap()

    override fun print(): String {
        val sb = StringBuilder()
        sb.append("switch (${getKey()}) {")
        getBranches().forEach { sb.append("${it.key} -> ${it.value.name}; ") }
        sb.append("else -> ${getDefault().name}}")
        return sb.toString()
    }

    override fun clone(): Instruction = SwitchInst(getKey(), getDefault(), getBranches())
}