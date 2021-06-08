package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.InvalidOpcodeException
import org.jetbrains.research.kfg.type.TypeFactory
import org.objectweb.asm.Opcodes

fun toBinaryOpcode(opcode: Int) = when (opcode) {
    in Opcodes.IADD..Opcodes.DADD -> BinaryOpcode.ADD
    in Opcodes.ISUB..Opcodes.DSUB -> BinaryOpcode.SUB
    in Opcodes.IMUL..Opcodes.DMUL -> BinaryOpcode.MUL
    in Opcodes.IDIV..Opcodes.DDIV -> BinaryOpcode.DIV
    in Opcodes.IREM..Opcodes.DREM -> BinaryOpcode.REM
    in Opcodes.ISHL..Opcodes.LSHL -> BinaryOpcode.SHL
    in Opcodes.ISHR..Opcodes.LSHR -> BinaryOpcode.SHR
    in Opcodes.IUSHR..Opcodes.LUSHR -> BinaryOpcode.USHR
    in Opcodes.IAND..Opcodes.LAND -> BinaryOpcode.AND
    in Opcodes.IOR..Opcodes.LOR -> BinaryOpcode.OR
    in Opcodes.IXOR..Opcodes.LXOR -> BinaryOpcode.XOR
    else -> throw InvalidOpcodeException("Binary opcode $opcode")
}

enum class BinaryOpcode(val opcode: String) {
    ADD("+"),
    SUB("-"),
    MUL("*"),
    DIV("/"),
    REM("%"),
    SHL("<<"),
    SHR(">>"),
    USHR("u>>"),
    AND("&&"),
    OR("||"),
    XOR("^");

    companion object {
        fun parse(string: String) = when (string.trim()) {
            "+" -> ADD
            "-" -> SUB
            "*" -> MUL
            "/" -> DIV
            "%" -> REM
            "<<" -> SHL
            ">>" -> SHR
            "u>>" -> USHR
            "&&" -> AND
            "||" -> OR
            "^" -> XOR
            else -> throw IllegalArgumentException("Unknown opcode $string")
        }
    }

    override fun toString() = opcode

    val asmOpcode: Int
        get() = when (this) {
            ADD -> Opcodes.IADD
            SUB -> Opcodes.ISUB
            MUL -> Opcodes.IMUL
            DIV -> Opcodes.IDIV
            REM -> Opcodes.IREM
            SHL -> Opcodes.ISHL
            SHR -> Opcodes.ISHR
            USHR -> Opcodes.IUSHR
            AND -> Opcodes.IAND
            OR -> Opcodes.IOR
            XOR -> Opcodes.IXOR
        }
}

fun toCmpOpcode(opcode: Int) = when (opcode) {
    Opcodes.IFNULL -> CmpOpcode.EQ
    Opcodes.IFNONNULL -> CmpOpcode.NEQ
    Opcodes.IFEQ -> CmpOpcode.EQ
    Opcodes.IFNE -> CmpOpcode.NEQ
    Opcodes.IFLT -> CmpOpcode.LT
    Opcodes.IFGE -> CmpOpcode.GE
    Opcodes.IFGT -> CmpOpcode.GT
    Opcodes.IFLE -> CmpOpcode.LE
    Opcodes.IF_ICMPEQ -> CmpOpcode.EQ
    Opcodes.IF_ICMPNE -> CmpOpcode.NEQ
    Opcodes.IF_ICMPLT -> CmpOpcode.LT
    Opcodes.IF_ICMPGE -> CmpOpcode.GE
    Opcodes.IF_ICMPGT -> CmpOpcode.GT
    Opcodes.IF_ICMPLE -> CmpOpcode.LE
    Opcodes.IF_ACMPEQ -> CmpOpcode.EQ
    Opcodes.IF_ACMPNE -> CmpOpcode.NEQ
    Opcodes.LCMP -> CmpOpcode.CMP
    Opcodes.FCMPL -> CmpOpcode.CMPL
    Opcodes.FCMPG -> CmpOpcode.CMPG
    Opcodes.DCMPL -> CmpOpcode.CMPL
    Opcodes.DCMPG -> CmpOpcode.CMPG
    else -> throw InvalidOpcodeException("Cmp opcode $opcode")
}

fun getCmpResultType(tf: TypeFactory, opcode: CmpOpcode) = when (opcode) {
    CmpOpcode.CMP -> tf.intType
    CmpOpcode.CMPL -> tf.intType
    CmpOpcode.CMPG -> tf.intType
    else -> tf.boolType
}

enum class CmpOpcode(val opcode: String) {
    EQ("=="),
    NEQ("!="),
    LT("<"),
    GT(">"),
    LE("<="),
    GE(">="),
    CMP("cmp"),
    CMPG("cmpg"),
    CMPL("cmpl");

    companion object {
        fun parse(string: String) = when (string.trim()) {
            "==" -> EQ
            "!=" -> NEQ
            "<" -> LT
            ">" -> GT
            "<=" -> LE
            ">=" -> GE
            "cmp" -> CMP
            "cmpg" -> CMPG
            "cmpl" -> CMPL
            else -> throw IllegalArgumentException("Unknown opcode $string")
        }
    }

    override fun toString() = opcode
}

fun toCallOpcode(opcode: Int): CallOpcode = when (opcode) {
    Opcodes.INVOKEINTERFACE -> CallOpcode.INTERFACE
    Opcodes.INVOKESTATIC -> CallOpcode.STATIC
    Opcodes.INVOKESPECIAL -> CallOpcode.SPECIAL
    Opcodes.INVOKEVIRTUAL -> CallOpcode.VIRTUAL
    else -> throw InvalidOpcodeException("Call opcode $opcode")
}

enum class CallOpcode(val opcode: String) {
    VIRTUAL("virtual"),
    SPECIAL("special"),
    STATIC("static"),
    INTERFACE("interface");


    companion object {
        fun parse(string: String) = when (string.trim()) {
            "virtual" -> VIRTUAL
            "special" -> SPECIAL
            "static" -> STATIC
            "interface" -> INTERFACE
            else -> throw IllegalArgumentException("Unknown opcode $string")
        }
    }

    override fun toString() = opcode

    val asmOpcode: Int
        get() = when (this) {
            VIRTUAL -> Opcodes.INVOKEVIRTUAL
            SPECIAL -> Opcodes.INVOKESPECIAL
            STATIC -> Opcodes.INVOKESTATIC
            INTERFACE -> Opcodes.INVOKEINTERFACE
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
    else -> throw InvalidOpcodeException("Unknown instruction opcode $opcode")
}