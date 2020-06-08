package org.jetbrains.research.kfg.type

import com.abdullin.kthelper.defaultHashCode

sealed class Integral : PrimaryType {
    override val bitsize get() = Type.WORD

    abstract val width: Int
    abstract val signed: Boolean

    override val isPrimary get() = true
    override val isIntegral get() = true

    open val isByte get() = false
    open val isShort get() = false
    open val isInt get() = false
    open val isLong get() = false
    open val isChar get() = false

    override fun toString() = this.name
    override fun hashCode() = defaultHashCode(BoolType.width, BoolType.signed)
    override fun equals(other: Any?) = this === other
}

object BoolType : Integral() {
    override val name = "bool"
    override val width = 32
    override val signed = false
    override val asmDesc = "Z"

    override fun isSubtypeOf(other: Type) = other is BoolType
}

object ByteType : Integral() {
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

object ShortType : Integral() {
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

object IntType : Integral() {
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

object LongType : Integral() {
    override val bitsize = Type.DWORD
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

object CharType : Integral() {
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