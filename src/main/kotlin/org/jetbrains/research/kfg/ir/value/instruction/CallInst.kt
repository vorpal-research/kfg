package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.ValueName

class CallInst : Instruction {
    val method: Method
    val `class`: Class
    val isStatic: Boolean

    constructor(method: Method, `class`: Class, args: Array<Value>)
            : super(UndefinedName.instance, method.retType, args) {
        this.method = method
        this.`class` = `class`
        this.isStatic = true
    }

    constructor(method: Method, `class`: Class, obj: Value, args: Array<Value>)
            : super(UndefinedName.instance, method.retType, arrayOf(obj).plus(args)) {
        this.method = method
        this.`class` = `class`
        this.isStatic = false
    }

    constructor(name: ValueName, method: Method, `class`: Class, args: Array<Value>)
            : super(name, method.retType, args) {
        this.method = method
        this.`class` = `class`
        this.isStatic = true
    }

    constructor(name: ValueName, method: Method, `class`: Class, obj: Value, args: Array<Value>)
            : super(name, method.retType, arrayOf(obj).plus(args)) {
        this.method = method
        this.`class` = `class`
        this.isStatic = false
    }

    fun getCallee(): Value? = if (isStatic) null else operands[0]
    fun getArgs(): Array<Value> = if (isStatic) operands else operands.drop(1).toTypedArray()

    override fun print(): String {
        val sb = StringBuilder()
        if (!type.isVoid()) sb.append("$name = ")
        if (isStatic) sb.append(`class`.name)
        else sb.append(operands[0].name)
        sb.append(".${method.name}(")
        getArgs().dropLast(1).forEach { sb.append("$it, ") }
        getArgs().takeLast(1).forEach { sb.append("$it") }
        sb.append(")")
        return sb.toString()
    }
}