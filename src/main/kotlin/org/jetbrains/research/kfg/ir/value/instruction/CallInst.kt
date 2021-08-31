package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.Name
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.UsageContext
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.VoidType
import org.jetbrains.research.kthelper.assert.asserted
import org.jetbrains.research.kthelper.assert.ktassert

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

    internal constructor(opcode: CallOpcode, method: Method, klass: Class, args: Array<Value>, ctx: UsageContext)
            : super(UndefinedName(), method.returnType, args, ctx) {
        this.opcode = opcode
        this.method = method
        this.klass = klass
        this.isStatic = true
    }

    internal constructor(opcode: CallOpcode, method: Method, klass: Class, obj: Value, args: Array<Value>, ctx: UsageContext)
            : super(UndefinedName(), method.returnType, arrayOf(obj).plus(args), ctx) {
        this.opcode = opcode
        this.method = method
        this.klass = klass
        this.isStatic = false
    }

    internal constructor(opcode: CallOpcode, name: Name, method: Method, klass: Class, args: Array<Value>, ctx: UsageContext)
            : super(name, method.returnType, args, ctx) {
        ktassert((method.returnType is VoidType && name is UndefinedName) || method.returnType !is VoidType, "named CallInst should not have type `VoidType`")
        this.opcode = opcode
        this.method = method
        this.klass = klass
        this.isStatic = true
    }

    internal constructor(opcode: CallOpcode, name: Name, method: Method, klass: Class, obj: Value, args: Array<Value>, ctx: UsageContext)
            : super(name, method.returnType, arrayOf(obj).plus(args), ctx) {
        ktassert((method.returnType is VoidType && name is UndefinedName) || method.returnType !is VoidType, "named CallInst should not have type `VoidType`")
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

    override fun clone(ctx: UsageContext): Instruction = when {
        isStatic -> CallInst(opcode, name.clone(), method, klass, args.toTypedArray(), ctx)
        else -> CallInst(opcode, name.clone(), method, klass, callee, args.toTypedArray(), ctx)
    }
}