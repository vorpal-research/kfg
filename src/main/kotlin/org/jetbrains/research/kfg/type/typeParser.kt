package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.InvalidTypeDescException
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.UnexpectedOpcodeException
import org.objectweb.asm.Opcodes
import java.util.regex.Pattern

fun toRealName(type: String) = type.replace('/', '.')

fun parseDesc(desc: String): Type {
    return when (desc[0]) {
        'V' -> TF.getVoidType()
        'Z' -> TF.getBoolType()
        'B' -> TF.getByteType()
        'C' -> TF.getCharType()
        'S' -> TF.getShortType()
        'I' -> TF.getIntType()
        'J' -> TF.getLongType()
        'F' -> TF.getFloatType()
        'D' -> TF.getDoubleType()
        'L' -> {
            if (desc.last() != ';') throw InvalidTypeDescException(desc)
            TF.getRefType(toRealName(desc.substringBeforeLast(';')))
        }
        '[' -> TF.getArrayType(parseDesc(desc.substring(1)))
        else -> throw InvalidTypeDescException(desc)
    }
}

fun parsePrimaryType(opcode: Int): Type {
    return when (opcode) {
        Opcodes.T_CHAR -> TF.getCharType()
        Opcodes.T_BOOLEAN -> TF.getBoolType()
        Opcodes.T_BYTE -> TF.getByteType()
        Opcodes.T_DOUBLE -> TF.getDoubleType()
        Opcodes.T_FLOAT -> TF.getFloatType()
        Opcodes.T_INT -> TF.getIntType()
        Opcodes.T_LONG -> TF.getLongType()
        Opcodes.T_SHORT -> TF.getShortType()
        else -> throw UnexpectedOpcodeException("PrimaryType opcode $opcode")
    }
}

fun parseMethodDesc(desc: String): Pair<Array<Type>, Type> {
    val args = mutableListOf<Type>()
    val pattern= Pattern.compile("\\[*(V|Z|B|C|S|I|J|F|D|(L[a-zA-Z$0-9\\/]+;))")
    val matcher = pattern.matcher(desc)
    while (matcher.find()) {
        args.add(parseDesc(matcher.group(0)))
    }
    val rettype = args.last()
    return Pair(args.dropLast(1).toTypedArray(), rettype)
}