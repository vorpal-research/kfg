package org.jetbrains.research.kfg.builder

import org.jetbrains.research.kfg.InvalidOpcodeException
import org.jetbrains.research.kfg.InvalidTypeDescException
import org.jetbrains.research.kfg.UnexpectedOpcodeException
import org.jetbrains.research.kfg.ir.InstFactory
import org.objectweb.asm.commons.JSRInlinerAdapter
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.parseDesc
import org.jetbrains.research.kfg.value.Value
import org.jetbrains.research.kfg.value.ValueFactory
import java.util.*

class MethodBuilder(val method: Method, desc: String, exceptions: Array<String>)
    : JSRInlinerAdapter(null, method.modifiers, method.name, desc, null, exceptions) {
    val VF = ValueFactory.instance
    val IF = InstFactory.instance
    val stack = Stack<Value>()

    private fun getConst(opcode: Int): Value = when {
        opcode == ACONST_NULL -> VF.getNullConstant()
        opcode in ICONST_M1..ICONST_5 -> VF.getIntConstant(opcode - ICONST_M1)
        opcode in LCONST_0..LCONST_1 -> VF.getLongConstant((opcode - LCONST_0).toLong())
        opcode in FCONST_0..FCONST_2 -> VF.getFloatConstant((opcode - FCONST_0).toFloat())
        opcode in DCONST_0..DCONST_1 -> VF.getDoubleConstant((opcode - DCONST_0).toDouble())
        else -> throw UnexpectedOpcodeException("Unknown const $opcode")
    }

    private fun getArrayLoad(opcode: Int) : Value {
        val index = stack.pop()
        val arrayRef = stack.pop()
        return IF.getArrayLoadInst(arrayRef, index)
    }

    override fun visitInsn(opcode: Int) {
        super.visitInsn(opcode)
        when {
            opcode == NOP -> {}
            opcode in ACONST_NULL..DCONST_1 -> stack.push(getConst(opcode))
            opcode in IALOAD..SALOAD -> stack.push(getArrayLoad(opcode))
            opcode in IASTORE..SASTORE -> {}
        }
    }

    override fun visitTypeInsn(opcode: Int, desc: String?) {
        super.visitTypeInsn(opcode, desc)
        if (desc.isNullOrEmpty()) throw InvalidTypeDescException("null")
        val type = parseDesc(desc!!)
        when (opcode) {
            NEW -> {
                stack.push(IF.getNewInst(type))
            }
            ANEWARRAY -> {
                val count = stack.pop()
                stack.push(IF.getNewArrayInst(type, count))
            }
            CHECKCAST -> {
                val castable = stack.pop()
                stack.push(IF.getCheckCastInst(type, castable))
            }
            INSTANCEOF -> {
                val obj = stack.pop()
                stack.push(IF.getInstanceOfInst(type, obj))
            }
            else -> InvalidOpcodeException("$opcode in TypeInsn")
        }
    }
}