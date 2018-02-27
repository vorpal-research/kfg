package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.ir.value.Value

class ReturnInst : Instruction {
    constructor() : super("", TypeFactory.instance.getVoidType(), arrayOf())
    constructor(retval: Value) : super("", retval.type, arrayOf(retval))

    fun hasReturnValue() = operands.isNotEmpty()
    fun getReturnType() = operands[0].type
    fun getReturnValue() = operands[0]

    override fun isTerminate() = true
    override fun print(): String {
        val sb = StringBuilder()
        sb.append("return ")
        if (hasReturnValue()) sb.append(getReturnValue())
        return sb.toString()
    }
}