package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.value.Value

class ReturnInst : TerminateInst {
    constructor() : super(UndefinedName, TF.getVoidType(), arrayOf(), arrayOf())
    constructor(retval: Value) : super(UndefinedName, retval.type, arrayOf(retval), arrayOf())

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