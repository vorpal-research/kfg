package org.jetbrains.research.kfg.ir.value.instruction

import com.abdullin.kthelper.defaultHashCode
import org.jetbrains.research.kfg.InvalidOpcodeError
import org.jetbrains.research.kfg.type.TypeFactory
import org.objectweb.asm.Opcodes

fun toBinaryOpcode(opcode: Int) = when (opcode) {
    in Opcodes.IADD..Opcodes.DADD -> BinaryOpcode.Add()
    in Opcodes.ISUB..Opcodes.DSUB -> BinaryOpcode.Sub()
    in Opcodes.IMUL..Opcodes.DMUL -> BinaryOpcode.Mul()
    in Opcodes.IDIV..Opcodes.DDIV -> BinaryOpcode.Div()
    in Opcodes.IREM..Opcodes.DREM -> BinaryOpcode.Rem()
    in Opcodes.ISHL..Opcodes.LSHL -> BinaryOpcode.Shl()
    in Opcodes.ISHR..Opcodes.LSHR -> BinaryOpcode.Shr()
    in Opcodes.IUSHR..Opcodes.LUSHR -> BinaryOpcode.Ushr()
    in Opcodes.IAND..Opcodes.LAND -> BinaryOpcode.And()
    in Opcodes.IOR..Opcodes.LOR -> BinaryOpcode.Or()
    in Opcodes.IXOR..Opcodes.LXOR -> BinaryOpcode.Xor()
    else -> throw InvalidOpcodeError("Binary opcode $opcode")
}

sealed class BinaryOpcode {
    abstract val name: String

    companion object {
        fun parse(string: String) = when (string.trim()) {
            "+" -> Add()
            "-" -> Sub()
            "*" -> Mul()
            "/" -> Div()
            "%" -> Rem()
            "<<" -> Shl()
            ">>" -> Shr()
            "u>>" -> Ushr()
            "&&" -> And()
            "||" -> Or()
            "^" -> Xor()
            else -> throw IllegalArgumentException("Unknown opcode $string")
        }
    }

    override fun toString() = name
    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as BinaryOpcode
        return this.name == other.name
    }

    class Add(override val name: String = "+") : BinaryOpcode()
    class Sub(override val name: String = "-") : BinaryOpcode()
    class Mul(override val name: String = "*") : BinaryOpcode()
    class Div(override val name: String = "/") : BinaryOpcode()
    class Rem(override val name: String = "%") : BinaryOpcode()
    class Shl(override val name: String = "<<") : BinaryOpcode()
    class Shr(override val name: String = ">>") : BinaryOpcode()
    class Ushr(override val name: String = "u>>") : BinaryOpcode()
    class And(override val name: String = "&&") : BinaryOpcode()
    class Or(override val name: String = "||") : BinaryOpcode()
    class Xor(override val name: String = "^") : BinaryOpcode()

    val asmOpcode: Int
        get() = when (this) {
            is Add -> Opcodes.IADD
            is Sub -> Opcodes.ISUB
            is Mul -> Opcodes.IMUL
            is Div -> Opcodes.IDIV
            is Rem -> Opcodes.IREM
            is Shl -> Opcodes.ISHL
            is Shr -> Opcodes.ISHR
            is Ushr -> Opcodes.IUSHR
            is And -> Opcodes.IAND
            is Or -> Opcodes.IOR
            is Xor -> Opcodes.IXOR
        }
}

fun toCmpOpcode(opcode: Int) = when (opcode) {
    Opcodes.IFNULL -> CmpOpcode.Eq()
    Opcodes.IFNONNULL -> CmpOpcode.Neq()
    Opcodes.IFEQ -> CmpOpcode.Eq()
    Opcodes.IFNE -> CmpOpcode.Neq()
    Opcodes.IFLT -> CmpOpcode.Lt()
    Opcodes.IFGE -> CmpOpcode.Ge()
    Opcodes.IFGT -> CmpOpcode.Gt()
    Opcodes.IFLE -> CmpOpcode.Le()
    Opcodes.IF_ICMPEQ -> CmpOpcode.Eq()
    Opcodes.IF_ICMPNE -> CmpOpcode.Neq()
    Opcodes.IF_ICMPLT -> CmpOpcode.Lt()
    Opcodes.IF_ICMPGE -> CmpOpcode.Ge()
    Opcodes.IF_ICMPGT -> CmpOpcode.Gt()
    Opcodes.IF_ICMPLE -> CmpOpcode.Le()
    Opcodes.IF_ACMPEQ -> CmpOpcode.Eq()
    Opcodes.IF_ACMPNE -> CmpOpcode.Neq()
    Opcodes.LCMP -> CmpOpcode.Cmp()
    Opcodes.FCMPL -> CmpOpcode.Cmpl()
    Opcodes.FCMPG -> CmpOpcode.Cmpg()
    Opcodes.DCMPL -> CmpOpcode.Cmpl()
    Opcodes.DCMPG -> CmpOpcode.Cmpg()
    else -> throw InvalidOpcodeError("Cmp opcode $opcode")
}

fun getCmpResultType(tf: TypeFactory, opcode: CmpOpcode) = when (opcode) {
    is CmpOpcode.Cmp -> tf.intType
    is CmpOpcode.Cmpl -> tf.intType
    is CmpOpcode.Cmpg -> tf.intType
    else -> tf.boolType
}

sealed class CmpOpcode {
    abstract val name: String

    companion object {
        fun parse(string: String) = when (string.trim()) {
            "==" -> Eq()
            "!=" -> Neq()
            "<" -> Lt()
            ">" -> Gt()
            "<=" -> Le()
            ">=" -> Ge()
            "cmp" -> Cmp()
            "cmpg" -> Cmpg()
            "cmpl" -> Cmpl()
            else -> throw IllegalArgumentException("Unknown opcode $string")
        }
    }

    override fun toString() = name
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CmpOpcode) return false

        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    class Eq(override val name: String = "==") : CmpOpcode()
    class Neq(override val name: String = "!=") : CmpOpcode()
    class Lt(override val name: String = "<") : CmpOpcode()
    class Gt(override val name: String = ">") : CmpOpcode()
    class Le(override val name: String = "<=") : CmpOpcode()
    class Ge(override val name: String = ">=") : CmpOpcode()
    class Cmp(override val name: String = "cmp") : CmpOpcode()
    class Cmpg(override val name: String = "cmpg") : CmpOpcode()
    class Cmpl(override val name: String = "cmpl") : CmpOpcode()
}

fun toCallOpcode(opcode: Int): CallOpcode = when (opcode) {
    Opcodes.INVOKEINTERFACE -> CallOpcode.Interface()
    Opcodes.INVOKESTATIC -> CallOpcode.Static()
    Opcodes.INVOKESPECIAL -> CallOpcode.Special()
    Opcodes.INVOKEVIRTUAL -> CallOpcode.Virtual()
    else -> throw InvalidOpcodeError("Call opcode $opcode")
}

sealed class CallOpcode {
    abstract val name: String

    companion object {
        fun parse(string: String) = when (string.trim()) {
            "virtual" -> Virtual()
            "special" -> Special()
            "static" -> Static()
            "interface" -> Interface()
            else -> throw IllegalArgumentException("Unknown opcode $string")
        }
    }

    override fun toString() = name

    class Virtual(override val name: String = "virtual") : CallOpcode()
    class Special(override val name: String = "special") : CallOpcode()
    class Static(override val name: String = "static") : CallOpcode()
    class Interface(override val name: String = "interface") : CallOpcode()

    val asmOpcode: Int
        get() = when (this) {
            is Virtual -> Opcodes.INVOKEVIRTUAL
            is Special -> Opcodes.INVOKESPECIAL
            is Static -> Opcodes.INVOKESTATIC
            is Interface -> Opcodes.INVOKEINTERFACE
        }
}

fun isTerminateInst(opcode: Int) = when (opcode) {
    Opcodes.TABLESWITCH -> true
    Opcodes.LOOKUPSWITCH -> true
    Opcodes.GOTO -> true
    Opcodes.ATHROW -> true
    in Opcodes.IRETURN..Opcodes.RETURN -> true
    else -> false
}

fun isExceptionThrowing(opcode: Int) = when (opcode) {
    in Opcodes.NOP..Opcodes.ALOAD -> false
    in Opcodes.IALOAD..Opcodes.SALOAD -> true
    in Opcodes.ISTORE..Opcodes.ASTORE -> false
    in Opcodes.IASTORE..Opcodes.SASTORE -> true
    in Opcodes.POP..Opcodes.DMUL -> false
    in Opcodes.IDIV..Opcodes.DREM -> true
    in Opcodes.INEG..Opcodes.PUTSTATIC -> false
    in Opcodes.GETFIELD..Opcodes.INVOKEDYNAMIC -> true
    Opcodes.NEW -> false
    in Opcodes.NEWARRAY..Opcodes.CHECKCAST -> true
    Opcodes.INSTANCEOF -> false
    in Opcodes.MONITORENTER..Opcodes.MULTIANEWARRAY -> true
    in Opcodes.IFNULL..Opcodes.IFNONNULL -> false
    else -> throw InvalidOpcodeError("Unknown instruction opcode $opcode")
}