package org.jetbrains.research.kfg.value

import jdk.internal.org.objectweb.asm.Opcodes
import org.jetbrains.research.kfg.UnexpectedOpcodeException
import org.jetbrains.research.kfg.value.expr.BinaryOpcode
import org.jetbrains.research.kfg.value.expr.CmpOpcode

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
