package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.ValueName

class CallInst : Instruction {
    val opcode: CallOpcode
    val method: Method
    val `class`: Class
    val isStatic: Boolean

    constructor(opcode: CallOpcode, method: Method, `class`: Class, args: Array<Value>)
            : super(UndefinedName, method.desc.retval, args) {
        this.opcode = opcode
        this.method = method
        this.`class` = `class`
        this.isStatic = true
    }

    constructor(opcode: CallOpcode, method: Method, `class`: Class, obj: Value, args: Array<Value>)
            : super(UndefinedName, method.desc.retval, arrayOf(obj).plus(args)) {
        this.opcode = opcode
        this.method = method
        this.`class` = `class`
        this.isStatic = false
    }

    constructor(opcode: CallOpcode, name: ValueName, method: Method, `class`: Class, args: Array<Value>)
            : super(name, method.desc.retval, args) {
        this.opcode = opcode
        this.method = method
        this.`class` = `class`
        this.isStatic = true
    }

    constructor(opcode: CallOpcode, name: ValueName, method: Method, `class`: Class, obj: Value, args: Array<Value>)
            : super(name, method.desc.retval, arrayOf(obj).plus(args)) {
        this.opcode = opcode
        this.method = method
        this.`class` = `class`
        this.isStatic = false
    }

    fun getCallee(): Value? = if (isStatic) null else operands[0]
    fun getArgs(): Array<Value> = if (isStatic) operands else operands.drop(1).toTypedArray()

    override fun print(): String {
        val sb = StringBuilder()
        if (name !is UndefinedName) sb.append("$name = ")
        sb.append("$opcode ")
        if (isStatic) sb.append(`class`.name)
        else sb.append(operands[0].name)
        sb.append(".${method.name}(")
        getArgs().dropLast(1).forEach { sb.append("$it, ") }
        getArgs().takeLast(1).forEach { sb.append("$it") }
        sb.append(")")
        return sb.toString()
    }
}