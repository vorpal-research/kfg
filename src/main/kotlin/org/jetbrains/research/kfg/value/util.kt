package org.jetbrains.research.kfg.value

import jdk.internal.org.objectweb.asm.Opcodes
import org.jetbrains.research.kfg.UnexpectedOpcodeException

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
    Opcodes.LCMP -> CmpOpcode.EQ
    Opcodes.FCMPL -> CmpOpcode.CMPL
    Opcodes.FCMPG -> CmpOpcode.CMPG
    Opcodes.DCMPL -> CmpOpcode.CMPL
    Opcodes.DCMPG -> CmpOpcode.CMPG
    else -> throw UnexpectedOpcodeException("Cmp opcode $opcode")
}
