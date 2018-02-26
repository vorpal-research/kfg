package org.jetbrains.research.kfg.ir.instruction

import org.jetbrains.research.kfg.value.Value

class ReturnInst : Instruction {
    constructor() : super(arrayOf())
    constructor(retval: Value) : super(arrayOf(retval))

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