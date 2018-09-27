package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.InvalidOpcodeError
import org.jetbrains.research.kfg.InvalidStateError
import org.jetbrains.research.kfg.InvalidTypeDescError
import org.jetbrains.research.kfg.TF
import org.objectweb.asm.Opcodes
import java.util.regex.Pattern

fun Type.toInternalDesc(): String = when {
    this.isPrimary -> this.asmDesc
    this is ClassType -> this.`class`.fullname
    this is ArrayType -> "[${(component as? ClassType)?.asmDesc ?: component.toInternalDesc()}"
    else -> throw InvalidStateError("Unknown type ${this.name}")
}

fun mergeTypes(types: Set<Type>): Type? = when {
    TF.nullType in types -> {
        val filtered = types.asSequence().filterNot { it == TF.nullType }.toSet()
        when {
            filtered.isEmpty() -> null
            else -> mergeTypes(filtered)
        }
    }
    types.size == 1 -> types.first()
    types.all { it is Integral } -> types.asSequence().map { it as Integral }.maxBy { it.width }
    types.all { it is ClassType } -> {
        val classes = types.map { it as ClassType }
        var result = TF.objectType
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

fun parseDesc(desc: String): Type = when (desc[0]) {
    'V' -> TF.voidType
    'Z' -> TF.boolType
    'B' -> TF.byteType
    'C' -> TF.charType
    'S' -> TF.shortType
    'I' -> TF.intType
    'J' -> TF.longType
    'F' -> TF.floatType
    'D' -> TF.doubleType
    'L' -> {
        if (desc.last() != ';') throw InvalidTypeDescError(desc)
        TF.getRefType(desc.substring(1).substringBeforeLast(';'))
    }
    '[' -> TF.getArrayType(parseDesc(desc.substring(1)))
    else -> throw InvalidTypeDescError(desc)
}

fun parsePrimaryType(opcode: Int): Type = when (opcode) {
    Opcodes.T_CHAR -> TF.charType
    Opcodes.T_BOOLEAN -> TF.boolType
    Opcodes.T_BYTE -> TF.byteType
    Opcodes.T_DOUBLE -> TF.doubleType
    Opcodes.T_FLOAT -> TF.floatType
    Opcodes.T_INT -> TF.intType
    Opcodes.T_LONG -> TF.longType
    Opcodes.T_SHORT -> TF.shortType
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

fun parseMethodDesc(desc: String): Pair<Array<Type>, Type> {
    val args = mutableListOf<Type>()
    val pattern = Pattern.compile("\\[*(V|Z|B|C|S|I|J|F|D|(L[a-zA-Z$0-9\\/_]+;))")
    val matcher = pattern.matcher(desc)
    while (matcher.find()) {
        args.add(parseDesc(matcher.group(0)))
    }
    val rettype = args.last()
    return Pair(args.dropLast(1).toTypedArray(), rettype)
}

fun parseStringToType(name: String) = when (name) {
    "null" -> TF.nullType
    "void" -> TF.voidType
    "bool" -> TF.boolType
    "short" -> TF.shortType
    "long" -> TF.longType
    "char" -> TF.charType
    "int" -> TF.intType
    "float" -> TF.floatType
    "double" -> TF.doubleType
    else -> {
        var arrCount = 0
        val end = name.dropLastWhile {
            if (it == '[') ++arrCount
            it == '[' || it == ']'
        }
        var subtype = TF.getRefType(end)
        while (arrCount > 0) {
            --arrCount
            subtype = TF.getArrayType(subtype)
        }
        subtype
    }
}

fun Type.getExpandedBitsize() = when (this) {
    is ClassType -> `class`.fields.values.fold(0) { acc, field -> acc + field.type.bitsize }
    else -> bitsize
}