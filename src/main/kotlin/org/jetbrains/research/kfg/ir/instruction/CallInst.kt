package org.jetbrains.research.kfg.ir.instruction

import org.jetbrains.research.kfg.value.Value

class CallInst : Instruction {
    val isVoid: Boolean

    constructor(callExpr: Value) : super(arrayOf(callExpr)) {
        this.isVoid = true
    }

    constructor(lhv: Value, callExpr: Value) : super(arrayOf(lhv, callExpr)) {
        this.isVoid = false
    }

    fun getLhv(): Value? = if (isVoid) null else operands[0]
    fun getCall() = if (isVoid) operands[0] else operands[1]

    override fun print(): String {
        val sb = StringBuilder()
        if (!isVoid) sb.append("${getLhv()} = ")
        sb.append(getCall())
        return sb.toString()
    }
}