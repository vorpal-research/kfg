package org.jetbrains.research.kfg.builder

import org.jetbrains.research.kfg.InvalidOpcodeException
import org.jetbrains.research.kfg.InvalidOperandException
import org.jetbrains.research.kfg.UnexpectedOpcodeException
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.ClassManager
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.instruction.Instruction
import org.jetbrains.research.kfg.ir.instruction.InstructionFactory
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.type.parseDesc
import org.jetbrains.research.kfg.type.parsePrimaryType
import org.jetbrains.research.kfg.value.*
import org.jetbrains.research.kfg.value.expr.BinaryOpcode
import org.jetbrains.research.kfg.value.expr.UnaryOpcode
import org.objectweb.asm.commons.JSRInlinerAdapter
import org.objectweb.asm.Handle
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import java.util.*

class MethodBuilder(val method: Method, val CM: ClassManager, desc: String, exceptions: Array<String>)
    : JSRInlinerAdapter(null, method.modifiers, method.name, desc, null, exceptions) {
    val TF = TypeFactory.instance
    val VF = ValueFactory.instance
    val EF = VF.exprFactory
    val IF = InstructionFactory.instance

    private val locals = mutableMapOf<Int, Value>()
    private val labels = mutableMapOf<Label, BasicBlock>()
    private val stack = Stack<Value>()
    private var currentBlock = BasicBlock("entry", method)
    private var successor: BasicBlock? = null
    private var cond: Value? = null

    private fun newInstruction(i: Instruction) {
        currentBlock.instructions.add(i)
        i.bb = currentBlock
    }

    private fun getBasicBlockByLabel(lbl: Label): BasicBlock {
        if (labels.contains(lbl)) {
            return labels[lbl]!!
        } else {
            val bb = BasicBlock(lbl.toString(), method)
            labels[lbl] = bb
            return bb
        }
    }

    private fun newBasicBlock(bb: BasicBlock) {
        currentBlock.addSuccessor(bb)
        bb.addPredecessor(currentBlock)
        method.addBasicBlock(bb)
        currentBlock = bb
    }

    private fun convertConst(opcode: Int) {
        val cnst = when {
            opcode == ACONST_NULL -> VF.getNullConstant()
            opcode in ICONST_M1..ICONST_5 -> VF.getIntConstant(opcode - ICONST_M1)
            opcode in LCONST_0..LCONST_1 -> VF.getLongConstant((opcode - LCONST_0).toLong())
            opcode in FCONST_0..FCONST_2 -> VF.getFloatConstant((opcode - FCONST_0).toFloat())
            opcode in DCONST_0..DCONST_1 -> VF.getDoubleConstant((opcode - DCONST_0).toDouble())
            else -> throw UnexpectedOpcodeException("Unknown const $opcode")
        }
        stack.push(cnst)
    }

    private fun convertArrayLoad(opcode: Int) {
        val index = stack.pop()
        val arrayRef = stack.pop()
        stack.push(EF.getArrayLoad(arrayRef, index))
    }

    private fun convertArrayStore(opcode: Int) {
        val value = stack.pop()
        val index = stack.pop()
        val array = stack.pop()
        newInstruction(IF.getStore(array, index, value))
    }

    private fun convertPop(opcode: Int) {
        when (opcode) {
            POP -> stack.pop()
            POP2 -> {
                val top = stack.pop()
                if (!top.type.isDWord()) stack.pop()
            }
            else -> throw UnexpectedOpcodeException("Pop opcode $opcode")
        }
    }

    private fun convertDup(opcode: Int) {
        when (opcode) {
            DUP -> stack.push(stack.peek())
            DUP_X1 -> {
                val top = stack.pop()
                val prev = stack.pop()
                stack.push(top)
                stack.push(prev)
                stack.push(top)
            }
            DUP_X2 -> {
                val val1 = stack.pop()
                val val2 = stack.pop()
                if (val2.type.isDWord()) {
                    stack.push(val1)
                    stack.push(val2)
                    stack.push(val1)
                } else {
                    val val3 = stack.pop()
                    stack.push(val1)
                    stack.push(val3)
                    stack.push(val2)
                    stack.push(val1)
                }
            }
            DUP2 -> {
                val top = stack.pop()
                if (top.type.isDWord()) {
                    stack.push(top)
                    stack.push(top)
                } else {
                    val bot = stack.pop()
                    stack.push(bot)
                    stack.push(top)
                    stack.push(bot)
                    stack.push(top)
                }
            }
            DUP2_X1 -> {
                val val1 = stack.pop()
                if (val1.type.isDWord()) {
                    val val2 = stack.pop()
                    stack.push(val1)
                    stack.push(val2)
                    stack.push(val1)
                } else {
                    val val2 = stack.pop()
                    val val3 = stack.pop()
                    stack.push(val2)
                    stack.push(val1)
                    stack.push(val3)
                    stack.push(val2)
                    stack.push(val1)
                }
            }
            DUP2_X2 ->  {
                val val1 = stack.pop()
                if (val1.type.isDWord()) {
                    val val2 = stack.pop()
                    val val3 = stack.pop()
                    stack.push(val1)
                    stack.push(val3)
                    stack.push(val2)
                    stack.push(val1)
                } else {
                    val val2 = stack.pop()
                    val val3 = stack.pop()
                    val val4 = stack.pop()
                    stack.push(val2)
                    stack.push(val1)
                    stack.push(val4)
                    stack.push(val3)
                    stack.push(val2)
                    stack.push(val1)
                }
            }
            else -> throw UnexpectedOpcodeException("Dup opcode $opcode")
        }
    }

    private fun convertSwap(opcode: Int) {
        val top = stack.pop()
        val bot = stack.pop()
        stack.push(top)
        stack.push(bot)
    }

    private fun convertBinary(opcode: Int) {
        val lhv = stack.pop()
        val rhv = stack.pop()
        val binOp = toBinaryOpcode(opcode)
        stack.push(EF.getBinary(binOp, lhv, rhv))
    }

    private fun convertUnary(opcode: Int) {
        val array = stack.pop()
        val op = when (opcode) {
            in INEG .. DNEG -> UnaryOpcode.NEG
            ARRAYLENGTH -> UnaryOpcode.LENGTH
            else -> throw InvalidOperandException("Unary opcode $opcode")
        }
        stack.push(EF.getUnary(op, array))
    }

    private fun convertCast(opcode: Int) {
        val op = stack.pop()
        val type = when (opcode) {
            in arrayOf(I2L, F2L, D2L) -> TF.getLongType()
            in arrayOf(I2F, L2F, D2F) -> TF.getFloatType()
            in arrayOf(I2D, L2D, F2D) -> TF.getDoubleType()
            in arrayOf(L2I, F2I, D2I) -> TF.getIntType()
            I2B -> TF.getByteType()
            I2C -> TF.getCharType()
            I2S -> TF.getShortType()
            else -> throw UnexpectedOpcodeException("Cast opcode $opcode")
        }
        stack.push(EF.getCast(type, op))
    }

    private fun convertCmp(opcode: Int) {
        val lhv = stack.pop()
        val rhv = stack.pop()
        val op = toCmpOpcode(opcode)
        stack.push(EF.getCmp(op, lhv, rhv))
    }

    private fun convertReturn(opcode: Int) {
        if (opcode == RETURN) {
            newInstruction(IF.getReturn())
        } else {
            val retval = stack.pop()
            newInstruction(IF.getReturn(retval))
        }
    }

    private fun convertLocalLoad(opcode: Int, `var`: Int) {
        stack.push(locals[`var`])
    }

    private fun convertLocalStore(opcode: Int, `var`: Int) {
        val local = locals[`var`] ?: throw InvalidOperandException("$`var` local is empty for store")
        val obj = stack.pop()
        newInstruction(IF.getAssign(local, obj))
    }

    private fun convertMonitor(opcode: Int) {
        val owner = stack.pop()
        when (opcode) {
            MONITORENTER -> newInstruction(IF.getEnterMonitor(owner))
            MONITOREXIT -> newInstruction(IF.getExitMonitor(owner))
            else -> throw UnexpectedOpcodeException("Monitor opcode $opcode")
        }
    }

    override fun visitCode() {
        super.visitCode()
        var indx = 0
        for (it in method.arguments) {
            locals[indx] = VF.getLocal(indx++, it)
        }
        val entryLbl = Label()
        labels[entryLbl] = currentBlock
    }

    override fun visitInsn(opcode: Int) {
        super.visitInsn(opcode)
        when (opcode) {
            NOP -> {}
            in ACONST_NULL..DCONST_1 -> convertConst(opcode)
            in IALOAD..SALOAD -> convertArrayLoad(opcode)
            in IASTORE..SASTORE -> convertArrayStore(opcode)
            in POP .. POP2 -> convertPop(opcode)
            in DUP .. DUP2_X2 -> convertDup(opcode)
            SWAP -> convertSwap(opcode)
            in IADD .. DREM -> convertBinary(opcode)
            in INEG .. DNEG -> convertUnary(opcode)
            in ISHL .. LXOR -> convertBinary(opcode)
            in I2L .. I2S -> convertCast(opcode)
            in LCMP .. DCMPG -> convertCmp(opcode)
            in IRETURN .. RETURN -> convertReturn(opcode)
            ARRAYLENGTH -> convertUnary(opcode)
            ATHROW -> TODO()
            in MONITORENTER .. MONITOREXIT -> convertMonitor(opcode)
            else -> throw UnexpectedOpcodeException("Insn opcode $opcode")
        }
    }

    override fun visitIntInsn(opcode: Int, operand: Int) {
        super.visitIntInsn(opcode, operand)
        when (opcode) {
            BIPUSH -> stack.push(VF.getIntConstant(operand))
            SIPUSH -> stack.push(VF.getIntConstant(operand))
            NEWARRAY -> {
                val type = parsePrimaryType(operand)
                val count = stack.pop()
                stack.push(EF.getNewArray(type, count))
            }
            else -> throw UnexpectedOpcodeException("IntInsn opcode $opcode")
        }
    }

    override fun visitVarInsn(opcode: Int, `var`: Int) {
        super.visitVarInsn(opcode, `var`)
        when (opcode) {
            in ISTORE .. ASTORE -> convertLocalStore(opcode, `var`)
            in ILOAD .. ALOAD -> convertLocalLoad(opcode, `var`)
            RET -> TODO()
            else -> throw UnexpectedOpcodeException("VarInsn opcode $opcode")
        }
    }

    override fun visitTypeInsn(opcode: Int, desc: String) {
        super.visitTypeInsn(opcode, desc)
        val type = parseDesc(desc)
        when (opcode) {
            NEW -> stack.push(EF.getNew(type))
            ANEWARRAY -> {
                val count = stack.pop()
                stack.push(EF.getNewArray(type, count))
            }
            CHECKCAST -> {
                val castable = stack.pop()
                stack.push(EF.getCheckCast(type, castable))
            }
            INSTANCEOF -> {
                val obj = stack.pop()
                stack.push(EF.getInstanceOf(obj))
            }
            else -> InvalidOpcodeException("$opcode in TypeInsn")
        }
    }

    override fun visitFieldInsn(opcode: Int, owner: String, name: String, desc: String) {
        super.visitFieldInsn(opcode, owner, name, desc)
        val fieldType = parseDesc(desc)
        val klass = CM.getClassByName(owner) ?: throw InvalidOperandException("Field owner $owner")
        when (opcode) {
            GETSTATIC -> {
                stack.push(VF.getField(name, klass, fieldType))
            }
            PUTSTATIC -> {
                val field = VF.getField(name, klass, fieldType)
                val value = stack.pop()
                newInstruction(IF.getAssign(field, value))
            }
            GETFIELD -> {
                val obj = stack.pop()
                stack.push(VF.getField(name, klass, fieldType, obj))
            }
            PUTFIELD -> {
                val value = stack.pop()
                val obj = stack.pop()
                val field = VF.getField(name, klass, fieldType, obj)
                newInstruction(IF.getAssign(field, value))
            }
        }
    }

    override fun visitMethodInsn(opcode: Int, owner: String, name: String, desc: String, itf: Boolean) {
        super.visitMethodInsn(opcode, owner, name, desc, itf)
        val klass = CM.getClassByName(owner) ?: throw InvalidOperandException("Field owner $owner")
        val method = klass.getMethod(name)
        TODO()
    }

    override fun visitInvokeDynamicInsn(name: String?, desc: String?, bsm: Handle?, vararg bsmArgs: Any?) {
        super.visitInvokeDynamicInsn(name, desc, bsm, *bsmArgs)
        TODO()
    }

    override fun visitJumpInsn(opcode: Int, lbl: Label?) {
        super.visitJumpInsn(opcode, lbl)
        if (lbl == null) throw InvalidOperandException("Null label in jump")

        var succ = getBasicBlockByLabel(lbl)
        if (opcode == GOTO) {
            newInstruction(IF.getJump(succ))
        } else {
            val lhv = stack.pop()
            val opc = toCmpOpcode(opcode)
            successor = succ
            cond = when (opcode) {
                in IFEQ .. IFLE -> EF.getCmp(opc, lhv, VF.getIntConstant(0))
                in IF_ICMPEQ .. IF_ACMPNE -> EF.getCmp(opc, lhv, stack.pop())
                in IFNULL .. IFNONNULL -> EF.getCmp(opc, lhv, VF.getNullConstant())
                else -> throw UnexpectedOpcodeException("Jump opcode $opcode")
            }
        }
    }

    override fun visitLabel(label: Label?) {
        super.visitLabel(label)
        if (label == null) throw InvalidOperandException("Null label in label")
        val next = getBasicBlockByLabel(label)
        if (cond != null && successor != null) {
            newInstruction(IF.getBranch(cond!!, next, successor!!))
        }
        newBasicBlock(next)
    }

    override fun visitLdcInsn(cst: Any) {
        super.visitLdcInsn(cst)
        when (cst) {
            is Int -> stack.push(VF.getIntConstant(cst))
            is Float -> stack.push(VF.getFloatConstant(cst))
            is Double -> stack.push(VF.getDoubleConstant(cst))
            is Long -> stack.push(VF.getLongConstant(cst))
            is String -> stack.push(VF.getStringConstant(cst))
            is org.objectweb.asm.Type -> stack.push(VF.getClassConstant(cst.descriptor))
            is org.objectweb.asm.Handle -> {
                val klass = CM.getClassByName(cst.owner)
                        ?: throw InvalidOperandException("Class ${cst.owner}")
                val method = klass.getMethod(cst.name)
                        ?: throw InvalidOperandException("Method ${cst.name} in ${cst.owner}")
                stack.push(VF.getMethodConstant(method))
            }
            else -> throw InvalidOperandException("Unknown object $cst")
        }
    }

    override fun visitIincInsn(`var`: Int, increment: Int) {
        super.visitIincInsn(`var`, increment)
        val lhv = locals[`var`] ?: throw InvalidOperandException("$`var` local is invalid")
        val rhv = EF.getBinary(BinaryOpcode.ADD, lhv, VF.getIntConstant(increment))
        newInstruction(IF.getAssign(lhv, rhv))
    }

    override fun visitTableSwitchInsn(min: Int, max: Int, dflt: Label?, vararg labels: Label?) {
        super.visitTableSwitchInsn(min, max, dflt, *labels)
        TODO()
    }

    override fun visitLookupSwitchInsn(dflt: Label?, keys: IntArray?, labels: Array<out Label>?) {
        super.visitLookupSwitchInsn(dflt, keys, labels)
        TODO()
    }

    override fun visitMultiANewArrayInsn(desc: String?, dims: Int) {
        super.visitMultiANewArrayInsn(desc, dims)
        TODO()
    }

    override fun visitTryCatchBlock(start: Label?, end: Label?, handler: Label?, type: String?) {
        super.visitTryCatchBlock(start, end, handler, type)
        TODO()
    }

    override fun visitEnd() {
        super.visitEnd()
        TODO()
    }
}