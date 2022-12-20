package org.vorpal.research.kfg.type

import org.vorpal.research.kthelper.toInt

sealed class PrimitiveType : Type() {
    override val isConcrete get() = true
}

sealed class Integer : PrimitiveType() {
    override val bitSize get() = WORD

    abstract val width: Int
    abstract val signed: Boolean

    override val isPrimitive get() = true
    override val isInteger get() = true

    open val isByte get() = false
    open val isShort get() = false
    open val isInt get() = false
    open val isLong get() = false
    open val isChar get() = false

    override fun toString() = name
    override fun hashCode() = width * signed.toInt()
    override fun equals(other: Any?) = this === other
}

object BoolType : Integer() {
    override val name = "bool"
    override val width = 32
    override val signed = false
    override val asmDesc = "Z"

    override fun isSubtypeOf(other: Type) = other is BoolType
}

object ByteType : Integer() {
    override val name = "byte"
    override val width = 8
    override val signed = true
    override val isByte = true
    override val asmDesc = "B"

    override fun isSubtypeOf(other: Type) = when (other) {
        is ByteType -> true
        is ShortType, is IntType, is LongType, is Real -> true
        else -> false
    }
}

object ShortType : Integer() {
    override val name = "short"
    override val width = 16
    override val signed = true
    override val isShort = true
    override val asmDesc = "S"

    override fun isSubtypeOf(other: Type) = when (other) {
        is ShortType -> true
        is IntType, is LongType, is Real -> true
        else -> false
    }
}

object IntType : Integer() {
    override val name = "int"
    override val width = 32
    override val signed = true
    override val isInt = true
    override val asmDesc = "I"

    override fun isSubtypeOf(other: Type) = when (other) {
        is IntType -> true
        is LongType, is Real -> true
        else -> false
    }
}

object LongType : Integer() {
    override val bitSize = DWORD
    override val name = "long"
    override val width = 64
    override val signed = true
    override val isLong = true
    override val isDWord = true
    override val asmDesc = "J"

    override fun isSubtypeOf(other: Type) = when (other) {
        is LongType -> true
        is Real -> true
        else -> false
    }
}

object CharType : Integer() {
    override val name = "char"
    override val width = 16
    override val signed = false
    override val isChar = true
    override val asmDesc = "C"

    override fun isSubtypeOf(other: Type) = when (other) {
        is CharType -> true
        is IntType, is LongType, is Real -> true
        else -> false
    }
}

sealed class Real : PrimitiveType() {
    override val isPrimitive get() = true
    override val isReal get() = true

    override fun toString() = name
    override fun hashCode() = name.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return this.javaClass == other?.javaClass
    }
}

object FloatType : Real() {
    override val bitSize = WORD
    override val name = "float"
    override val asmDesc = "F"

    override fun isSubtypeOf(other: Type) = when (other) {
        is FloatType -> true
        is DoubleType -> true
        else -> false
    }
}

object DoubleType : Real() {
    override val bitSize = DWORD
    override val name = "double"
    override val isDWord = true
    override val asmDesc = "D"

    override fun isSubtypeOf(other: Type) = when (other) {
        is DoubleType -> true
        else -> false
    }
}
