package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.Name
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kthelper.assert.asserted

class CallInst : Instruction {
    val opcode: CallOpcode
    val method: Method
    val klass: Class
    val isStatic: Boolean

    val callee: Value
        get() = asserted(!isStatic) { ops[0] }

    val args: List<Value>
        get() = when {
            isStatic -> ops.toList()
            else -> ops.drop(1)
        }

    constructor(opcode: CallOpcode, method: Method, klass: Class, args: Array<Value>)
            : super(UndefinedName, method.returnType, args) {
        this.opcode = opcode
        this.method = method
        this.klass = klass
        this.isStatic = true
    }

    constructor(opcode: CallOpcode, method: Method, klass: Class, obj: Value, args: Array<Value>)
            : super(UndefinedName, method.returnType, arrayOf(obj).plus(args)) {
        this.opcode = opcode
        this.method = method
        this.klass = klass
        this.isStatic = false
    }

    constructor(opcode: CallOpcode, name: Name, method: Method, klass: Class, args: Array<Value>)
            : super(name, method.returnType, args) {
        this.opcode = opcode
        this.method = method
        this.klass = klass
        this.isStatic = true
    }

    constructor(opcode: CallOpcode, name: Name, method: Method, klass: Class, obj: Value, args: Array<Value>)
            : super(name, method.returnType, arrayOf(obj).plus(args)) {
        this.opcode = opcode
        this.method = method
        this.klass = klass
        this.isStatic = false
    }

    override fun print(): String {
        val sb = StringBuilder()
        if (name !is UndefinedName) sb.append("$name = ")

        sb.append("$opcode ")
        if (isStatic) sb.append(klass.name)
        else sb.append(callee.name)
        sb.append(".${method.name}(")
        sb.append(args.joinToString())
        sb.append(")")
        return sb.toString()
    }

    override fun clone(): Instruction = when {
        isStatic -> CallInst(opcode, name.clone(), method, klass, args.toTypedArray())
        else -> CallInst(opcode, name.clone(), method, klass, callee, args.toTypedArray())
    }
}