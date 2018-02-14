package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.InvalidTypeDescException

fun toRealName(type: String) = type.replace('/', '.')

fun parseDesc(desc: String): Type {
    val tf = TypeFactory.instance
    return when (desc[0]) {
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