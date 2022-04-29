package org.vorpal.research.kfg.type

import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.FrameNode
import org.vorpal.research.kfg.InvalidOpcodeException
import org.vorpal.research.kfg.InvalidStateException
import org.vorpal.research.kfg.InvalidTypeException
import java.util.regex.Pattern

val FrameNode.frameType get() = FrameNodeHelper.getFrameType(this)

val Type.internalDesc: String
    get() = when {
        this.isPrimary -> this.asmDesc
        this is ClassType -> this.klass.fullName
        this is ArrayType -> "[${(component as? ClassType)?.asmDesc ?: component.internalDesc}"
        else -> throw InvalidStateException("Unknown type ${this.name}")
    }

fun mergeTypes(tf: TypeFactory, types: Set<Type>): Type? = when {
    tf.nullType in types -> {
        val filtered = types.filterNot { it == tf.nullType }.toSet()
        when {
            filtered.isEmpty() -> tf.nullType
            else -> mergeTypes(tf, filtered)
        }
    }
    types.size == 1 -> types.first()
    types.all { it is Integral } -> types.map { it as Integral }.maxByOrNull { it.width }
    types.all { it is ClassType } -> {
        val classes = types.map { it as ClassType }
        var result = tf.objectType
        for (i in 0..classes.lastIndex) {
            val isAncestor = classes.fold(true) { acc, klass ->
                acc && classes[i].klass.isAncestorOf(klass.klass)
            }

            if (isAncestor) {
                result = classes[i]
            }
        }
        result
    }
    types.all { it is Reference } -> when {
        types.any { it is ClassType } -> tf.objectType
        types.map { it as ArrayType }.map { it.component }.toSet().size == 1 -> types.first()
        types.all { it is ArrayType } -> {
            val components = types.map { (it as ArrayType).component }.toSet()
            when (val merged = mergeTypes(tf, components)) {
                null -> tf.objectType
                else -> tf.getArrayType(merged)
            }
        }
        else -> tf.objectType
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
        if (desc.last() != ';') throw InvalidTypeException(desc)
        tf.getRefType(desc.drop(1).dropLast(1))
    }
    '[' -> tf.getArrayType(parseDesc(tf, desc.drop(1)))
    else -> throw InvalidTypeException(desc)
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
    else -> throw InvalidOpcodeException("PrimaryType opcode $opcode")
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
    else -> throw InvalidOpcodeException("${type.name} is not primary type")
}

fun parseMethodDesc(tf: TypeFactory, desc: String): Pair<Array<Type>, Type> {
    val args = mutableListOf<Type>()
    val pattern = Pattern.compile("\\[*(V|Z|B|C|S|I|J|F|D|(L[^;]+;))")
    val matcher = pattern.matcher(desc)
    while (matcher.find()) {
        args.add(parseDesc(tf, matcher.group(0)))
    }
    val returnType = args.last()
    return Pair(args.dropLast(1).toTypedArray(), returnType)
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

val Type.expandedBitSize
    get() = when (this) {
        is ClassType -> klass.fields.fold(0) { acc, field -> acc + field.type.bitSize }
        else -> bitSize
    }