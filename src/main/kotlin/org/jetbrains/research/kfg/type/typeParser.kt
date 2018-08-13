package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.InvalidTypeDescError
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.InvalidStateError
import org.jetbrains.research.kfg.InvalidOpcodeError
import org.objectweb.asm.Opcodes
import java.util.regex.Pattern

fun Type.toInternalDesc(): String = when {
    this.isPrimary() -> this.getAsmDesc()
    this is ClassType -> this.`class`.getFullname()
    this is ArrayType -> "[${(component as? ClassType)?.getAsmDesc() ?: component.toInternalDesc()}"
    else -> throw InvalidStateError("Unknown type ${this.name}")
}

fun mergeTypes(types: Set<Type>): Type? = when {
    TF.getNullType() in types -> {
        val filtered = types.filterNot { it == TF.getNullType() }.toSet()
        when {
            filtered.isEmpty() -> null
            else -> mergeTypes(filtered)
        }
    }
    types.size == 1 -> types.first()
    types.all { it is Integral } -> types.map { it as Integral }.maxBy { it.width }
    types.all { it is ClassType } -> {
        val classes = types.map { it as ClassType }
        var result = TF.getObject()
        for (i in 0..classes.lastIndex) {
            val isAncestor = classes.fold(true, { acc, `class` ->
                acc && classes[i].`class`.isAncestor(`class`.`class`)
            })

            if (isAncestor) {
                result = classes[i]
            }
        }
        result
    }
    else -> null
}

fun parseDesc(desc: String): Type = when (desc[0]) {
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
        if (desc.last() != ';') throw InvalidTypeDescError(desc)
        TF.getRefType(desc.substring(1).substringBeforeLast(';'))
    }
    '[' -> TF.getArrayType(parseDesc(desc.substring(1)))
    else -> throw InvalidTypeDescError(desc)
}

fun parsePrimaryType(opcode: Int): Type = when (opcode) {
    Opcodes.T_CHAR -> TF.getCharType()
    Opcodes.T_BOOLEAN -> TF.getBoolType()
    Opcodes.T_BYTE -> TF.getByteType()
    Opcodes.T_DOUBLE -> TF.getDoubleType()
    Opcodes.T_FLOAT -> TF.getFloatType()
    Opcodes.T_INT -> TF.getIntType()
    Opcodes.T_LONG -> TF.getLongType()
    Opcodes.T_SHORT -> TF.getShortType()
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
    "null" -> TF.getNullType()
    "void" -> TF.getVoidType()
    "bool" -> TF.getBoolType()
    "short" -> TF.getShortType()
    "long" -> TF.getLongType()
    "char" -> TF.getCharType()
    "int" -> TF.getIntType()
    "float" -> TF.getFloatType()
    "double" -> TF.getDoubleType()
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