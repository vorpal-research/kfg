package org.jetbrains.research.kfg.value.expr

import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.value.Value

enum class CallType {
    INVOKEVIRTUAL,
    INVOKESPECIAL,
    INVOKESTATIC,
    INVOKEINTERFACE
}

class CallExpr : Expr {
    val method: Method
    val klass: Class
    val isStatic: Boolean

    constructor(type: Type, method: Method, klass: Class, args: Array<Value>) : super(type, args) {
        this.method = method
        this.klass = klass
        this.isStatic = true
    }

    constructor(type: Type, method: Method, klass: Class, obj: Value, args: Array<Value>) : super(type, arrayOf(obj).plus(args)) {
        this.method = method
        this.klass = klass
        this.isStatic = false
    }

    fun getObject(): Value? = if (isStatic) null else operands[0]
    fun getArguments() = if (isStatic) operands else operands.drop(1).toTypedArray()

    override fun getName(): String {
        val sb = StringBuilder()
        if (isStatic) sb.append(klass.name)
        else sb.append(operands[0].getName())
        sb.append(".${method.name}(")
        getArguments().dropLast(1).forEach { sb.append("$it, ") }
        getArguments().takeLast(1).forEach { sb.append("$it") }
        sb.append(")")
        return sb.toString()
    }
}