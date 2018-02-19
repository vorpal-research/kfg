package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.InvalidTypeDescException
import org.jetbrains.research.kfg.UnexpectedOpcodeException
import org.objectweb.asm.Opcodes
import java.util.regex.Pattern

fun toRealName(type: String) = type.replace('/', '.')

fun parseDesc(desc: String): Type {
    val tf = TypeFactory.instance
    return when (desc[0]) {
        'V' -> tf.getVoidType()
        'Z' -> tf.getBoolType()
        'B' -> tf.getByteType()
        'C' -> tf.getCharType()
        'S' -> tf.getShortType()
        'I' -> tf.getIntType()
        'J' -> tf.getLongType()
        'F' -> tf.getFloatType()
        'D' -> tf.getDoubleType()
        'L' -> {
            if (desc.last() != ';') throw InvalidTypeDescException(desc)
            tf.getRefType(toRealName(desc.substringBeforeLast(';')))
        }
        '[' -> tf.getArrayType(parseDesc(desc.substring(1)))
        else -> throw InvalidTypeDescException(desc)
    }
}

fun parsePrimaryType(opcode: Int): Type {
    val tf = TypeFactory.instance
    return when (opcode) {
        Opcodes.T_CHAR -> tf.getCharType()
        Opcodes.T_BOOLEAN -> tf.getBoolType()
        Opcodes.T_BYTE -> tf.getByteType()
        Opcodes.T_DOUBLE -> tf.getDoubleType()
        Opcodes.T_FLOAT -> tf.getFloatType()
        Opcodes.T_INT -> tf.getIntType()
        Opcodes.T_LONG -> tf.getLongType()
        Opcodes.T_SHORT -> tf.getShortType()
        else -> throw UnexpectedOpcodeException("PrimaryType opcode $opcode")
    }
}

fun parseMethodDesc(desc: String): Pair<Array<Type>, Type> {
    val tf = TypeFactory.instance
    val args = mutableListOf<Type>()
    val pattern= Pattern.compile("\\[*(V|Z|B|C|S|I|J|F|D|(L[a-zA-Z\\/]+;))")
    val matcher = pattern.matcher(desc)
    while (matcher.find()) {
        args.add(parseDesc(matcher.group(0)))
    }
    val rettype = args.last()
    return Pair(args.dropLast(1).toTypedArray(), rettype)
}