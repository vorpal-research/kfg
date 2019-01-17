package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.InvalidOpcodeError
import org.jetbrains.research.kfg.InvalidStateError
import org.jetbrains.research.kfg.InvalidTypeDescError
import org.objectweb.asm.Opcodes
import java.util.regex.Pattern

fun Type.toInternalDesc(): String = when {
    this.isPrimary -> this.asmDesc
    this is ClassType -> this.`class`.fullname
    this is ArrayType -> "[${(component as? ClassType)?.asmDesc ?: component.toInternalDesc()}"
    else -> throw InvalidStateError("Unknown type ${this.name}")
}

fun mergeTypes(tf: TypeFactory, types: Set<Type>): Type? = when {
    tf.nullType in types -> {
        val filtered = types.asSequence().filterNot { it == tf.nullType }.toSet()
        when {
            filtered.isEmpty() -> tf.nullType
            else -> mergeTypes(tf, filtered)
        }
    }
    types.size == 1 -> types.first()
    types.all { it is Integral } -> types.asSequence().map { it as Integral }.maxBy { it.width }
    types.all { it is ClassType } -> {
        val classes = types.map { it as ClassType }
        var result = tf.objectType
        for (i in 0..classes.lastIndex) {
            val isAncestor = classes.fold(true) { acc, `class` ->
                acc && classes[i].`class`.isAncestor(`class`.`class`)
            }

            if (isAncestor) {
                result = classes[i]
            }
        }
        result
    }
    else -> null
}

fun parseDesc(tf: TypeFactory, desc: String): Type = when (desc[0]) {
    'V' -> tf.voidType
    'Z' -> tf.boolType
    'B' -> tf.byteType
    'C' -> tf.charType
    'S' -> tf.shortType
    'I' -> tf.intType
    'J' -> tf.longType
    'F' -> tf.floatType
    'D' -> tf.doubleType
    'L' -> {
        if (desc.last() != ';') throw InvalidTypeDescError(desc)
        tf.getRefType(desc.substring(1).substringBeforeLast(';'))
    }
    '[' -> tf.getArrayType(parseDesc(tf, desc.substring(1)))
    else -> throw InvalidTypeDescError(desc)
}

fun parsePrimaryType(tf: TypeFactory, opcode: Int): Type = when (opcode) {
    Opcodes.T_CHAR -> tf.charType
    Opcodes.T_BOOLEAN -> tf.boolType
    Opcodes.T_BYTE -> tf.byteType
    Opcodes.T_DOUBLE -> tf.doubleType
    Opcodes.T_FLOAT -> tf.floatType
    Opcodes.T_INT -> tf.intType
    Opcodes.T_LONG -> tf.longType
    Opcodes.T_SHORT -> tf.shortType
    else -> throw InvalidOpcodeError("PrimaryType opcode $opcode")
}

fun primaryTypeToInt(type: Type): Int = when (type) {
    is CharType -> Opcodes.T_CHAR
    is BoolType -> Opcodes.T_BOOLEAN
    is ByteType -> Opcodes.T_BYTE
    is DoubleType -> Opcodes.T_DOUBLE
    is FloatType -> Opcodes.T_FLOAT
    is IntType -> Opcodes.T_INT
    is LongType -> Opcodes.T_LONG
    is ShortType -> Opcodes.T_SHORT
    else -> throw InvalidOpcodeError("${type.name} is not primary type")
}

fun parseMethodDesc(tf: TypeFactory, desc: String): Pair<Array<Type>, Type> {
    val args = mutableListOf<Type>()
    val pattern = Pattern.compile("\\[*(V|Z|B|C|S|I|J|F|D|(L[a-zA-Z$0-9/_]+;))")
    val matcher = pattern.matcher(desc)
    while (matcher.find()) {
        args.add(parseDesc(tf, matcher.group(0)))
    }
    val rettype = args.last()
    return Pair(args.dropLast(1).toTypedArray(), rettype)
}

private fun parseNamedType(tf: TypeFactory, name: String): Type? = when (name) {
    "null" -> tf.nullType
    "void" -> tf.voidType
    "bool" -> tf.boolType
    "short" -> tf.shortType
    "long" -> tf.longType
    "char" -> tf.charType
    "int" -> tf.intType
    "float" -> tf.floatType
    "double" -> tf.doubleType
    else -> null
}

fun parseStringToType(tf: TypeFactory, name: String): Type {
    val namedType = parseNamedType(tf, name)
    if (namedType != null) return namedType

    var arrCount = 0
    val end = name.dropLastWhile {
        if (it == '[') ++arrCount
        it == '[' || it == ']'
    }
    var subtype = parseNamedType(tf, end) ?: tf.getRefType(end)
    while (arrCount > 0) {
        --arrCount
        subtype = tf.getArrayType(subtype)
    }
    return subtype
}

fun Type.getExpandedBitsize() = when (this) {
    is ClassType -> `class`.fields.values.fold(0) { acc, field -> acc + field.type.bitsize }
    else -> bitsize
}