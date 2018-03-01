package org.jetbrains.research.kfg.ir.value.instruction

import jdk.internal.org.objectweb.asm.Opcodes
import org.jetbrains.research.kfg.UnexpectedOpcodeException

enum class BinaryOpcode {
    ADD,
    SUB,
    MUL,
    DIV,
    REM,
    SHL,
    SHR,
    USHR,
    AND,
    OR,
    XOR
}

enum class CmpOpcode {
    EQ,
    NE,
    LT,
    GT,
    LE,
    GE,
    CMPG,
    CMPL
}

fun CmpOpcode.print(): String = when (this) {
    CmpOpcode.EQ -> "=="
    CmpOpcode.NE -> "!="
    CmpOpcode.LT -> "<"
    CmpOpcode.GT -> ">"
    CmpOpcode.LE -> "<="
    CmpOpcode.GE -> ">="
    CmpOpcode.CMPG -> "cmpg"
    CmpOpcode.CMPL -> "cmpl"
}

fun BinaryOpcode.print(): String = when (this) {
    BinaryOpcode.ADD -> "+"
    BinaryOpcode.SUB -> "-"
    BinaryOpcode.MUL -> "*"
    BinaryOpcode.DIV -> "/"
    BinaryOpcode.REM -> "%"
    BinaryOpcode.SHL -> "<<"
    BinaryOpcode.SHR -> ">>"
    BinaryOpcode.USHR -> "u>>"
    BinaryOpcode.AND -> "&&"
    BinaryOpcode.OR -> "||"
    BinaryOpcode.XOR -> "^"
}

fun toBinaryOpcode(opcode: Int) = when (opcode) {
    in Opcodes.IADD .. Opcodes.DADD -> BinaryOpcode.ADD
    in Opcodes.ISUB .. Opcodes.DSUB -> BinaryOpcode.SUB
    in Opcodes.IMUL .. Opcodes.DMUL -> BinaryOpcode.MUL
    in Opcodes.IDIV .. Opcodes.DDIV -> BinaryOpcode.DIV
    in Opcodes.IREM .. Opcodes.DREM -> BinaryOpcode.REM
    in Opcodes.ISHL .. Opcodes.LSHL -> BinaryOpcode.SHL
    in Opcodes.ISHR .. Opcodes.LSHR -> BinaryOpcode.SHR
    in Opcodes.IUSHR .. Opcodes.LUSHR -> BinaryOpcode.USHR
    in Opcodes.IAND .. Opcodes.LAND -> BinaryOpcode.AND
    in Opcodes.IOR .. Opcodes.LOR -> BinaryOpcode.OR
    in Opcodes.IXOR .. Opcodes.LXOR -> BinaryOpcode.XOR
    else -> throw UnexpectedOpcodeException("Binary opcode $opcode")
}

fun toCmpOpcode(opcode: Int) = when (opcode) {
    Opcodes.IFNULL -> CmpOpcode.EQ
    Opcodes.IFNONNULL -> CmpOpcode.NE
    Opcodes.IFEQ -> CmpOpcode.EQ
    Opcodes.IFNE -> CmpOpcode.NE
    Opcodes.IFLT -> CmpOpcode.LT
    Opcodes.IFGE -> CmpOpcode.GE
    Opcodes.IFGT -> CmpOpcode.GT
    Opcodes.IFLE -> CmpOpcode.LE
    Opcodes.IF_ICMPEQ -> CmpOpcode.EQ
    Opcodes.IF_ICMPNE -> CmpOpcode.NE
    Opcodes.IF_ICMPLT -> CmpOpcode.LT
    Opcodes.IF_ICMPGE -> CmpOpcode.GE
    Opcodes.IF_ICMPGT -> CmpOpcode.GT
    Opcodes.IF_ICMPLE -> CmpOpcode.LE
    Opcodes.IF_ACMPEQ -> CmpOpcode.EQ
    Opcodes.IF_ACMPNE -> CmpOpcode.NE
    Opcodes.LCMP -> CmpOpcode.EQ
    Opcodes.FCMPL -> CmpOpcode.CMPL
    Opcodes.FCMPG -> CmpOpcode.CMPG
    Opcodes.DCMPL -> CmpOpcode.CMPL
    Opcodes.DCMPG -> CmpOpcode.CMPG
    else -> throw UnexpectedOpcodeException("Cmp opcode $opcode")
}
