package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.InvalidTypeDescException
import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.UnexpectedException
import org.jetbrains.research.kfg.UnexpectedOpcodeException
import org.objectweb.asm.Opcodes
import java.util.regex.Pattern

fun Type.toInternalDesc(): String =
        if (this.isPrimary()) this.getAsmDesc()
        else if (this is ClassType) this.`class`.getFullname()
        else if (this is ArrayType) {
            val sub = if (component is ClassType) component.getAsmDesc()
            else component.toInternalDesc()
            "[$sub"
        }
        else throw UnexpectedException("Unknown type ${this.name}")

fun mergeTypes(types: Set<Type>) : Type? {
    if (types.size == 1) return types.first()
    else if (types.size == 2 && TF.getNullType() in types) {
        return if (types.first() == TF.getNullType()) types.last() else types.first()
    }
    val integrals = types.mapNotNull { it as? Integral }
    if (integrals.size == types.size) return integrals.maxBy { it.width }

    val classes = types.mapNotNull { it as? ClassType }
    if (classes.size == types.size) {
        for (i in 0 until classes.size) {
            val current = classes[i]
            val isAncestor = classes.foldRight(true, { `class`, acc -> acc && current.`class`.isAncestor(`class`.`class`) })
            if (isAncestor) return current
        }
    }
    return null
}

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
            TF.getRefType(desc.substring(1).substringBeforeLast(';'))
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

fun primaryTypeToInt(type: Type): Int {
    return when (type) {
        is CharType -> Opcodes.T_CHAR
        is BoolType -> Opcodes.T_BOOLEAN
        is ByteType -> Opcodes.T_BYTE
        is DoubleType -> Opcodes.T_DOUBLE
        is FloatType -> Opcodes.T_FLOAT
        is IntType -> Opcodes.T_INT
        is LongType -> Opcodes.T_LONG
        is ShortType -> Opcodes.T_SHORT
        else -> throw UnexpectedOpcodeException("${type.name} is not primary type")
    }
}

fun parseMethodDesc(desc: String): Pair<Array<Type>, Type> {
    val args = mutableListOf<Type>()
    val pattern= Pattern.compile("\\[*(V|Z|B|C|S|I|J|F|D|(L[a-zA-Z$0-9\\/_]+;))")
    val matcher = pattern.matcher(desc)
    while (matcher.find()) {
        args.add(parseDesc(matcher.group(0)))
    }
    val rettype = args.last()
    return Pair(args.dropLast(1).toTypedArray(), rettype)
}