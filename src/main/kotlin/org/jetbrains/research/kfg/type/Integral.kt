package org.jetbrains.research.kfg.type

import com.abdullin.kthelper.util.defaultHashCode

interface Integral : Type {
    override val bitsize get() = Type.WORD

    val width: Int
    val signed: Boolean

    override val isPrimary get() = true
    override val isIntegral get() = true

    val isByte get() = false
    val isShort get() = false
    val isInt get() = false
    val isLong get() = false
    val isChar get() = false
}

object BoolType : Integral {
    override val name = "bool"
    override val width = 32
    override val signed = false
    override val asmDesc = "Z"

    override fun toString() = name
    override fun hashCode() = defaultHashCode(width, signed)
    override fun equals(other: Any?) = this === other
}

object ByteType : Integral {
    override val name = "byte"
    override val width = 8
    override val signed = true
    override val isByte = true
    override val asmDesc = "B"

    override fun toString() = name
    override fun hashCode() = defaultHashCode(width, signed)
    override fun equals(other: Any?) = this === other
}

object ShortType : Integral {
    override val name = "short"
    override val width = 16
    override val signed = true
    override val isShort = true
    override val asmDesc = "S"

    override fun toString() = name
    override fun hashCode() = defaultHashCode(width, signed)
    override fun equals(other: Any?) = this === other
}

object IntType : Integral {
    override val name = "int"
    override val width = 32
    override val signed = true
    override val isInt = true
    override val asmDesc = "I"

    override fun toString() = name
    override fun hashCode() = defaultHashCode(width, signed)
    override fun equals(other: Any?) = this === other
}

object LongType : Integral {
    override val bitsize = Type.DWORD
    override val name = "long"
    override val width = 64
    override val signed = true
    override val isLong = true
    override val isDWord = true
    override val asmDesc = "J"

    override fun toString() = name
    override fun hashCode() = defaultHashCode(width, signed)
    override fun equals(other: Any?) = this === other
}

object CharType : Integral {
    override val name = "char"
    override val width = 16
    override val signed = false
    override val isChar = true
    override val asmDesc = "C"

    override fun toString() = name
    override fun hashCode() = defaultHashCode(width, signed)
    override fun equals(other: Any?) = this === other
}