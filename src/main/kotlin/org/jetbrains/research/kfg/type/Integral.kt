package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.util.simpleHash

interface Integral : Type {
    override val bitsize: Int
        get() = Type.WORD

    val width: Int
    val signed: Boolean

    override val isPrimary
        get() = true

    override val isIntegral
        get() = true

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

    override val asmDesc
        get() = "Z"

    override fun toString() = name
    override fun hashCode() = simpleHash(width, signed)
    override fun equals(other: Any?) = this === other
}

object ByteType : Integral {
    override val name = "byte"
    override val width = 8
    override val signed = true
    override val isByte get() = true

    override val asmDesc
        get() = "B"

    override fun toString() = name
    override fun hashCode() = simpleHash(width, signed)
    override fun equals(other: Any?) = this === other
}

object ShortType : Integral {
    override val name = "short"
    override val width = 16
    override val signed = true
    override val isShort get() = true
    override val asmDesc get() = "S"

    override fun toString() = name
    override fun hashCode() = simpleHash(width, signed)
    override fun equals(other: Any?) = this === other
}

object IntType : Integral {
    override val name = "int"
    override val width = 32
    override val signed = true
    override val isInt get() = true
    override val asmDesc get() = "I"

    override fun toString() = name
    override fun hashCode() = simpleHash(width, signed)
    override fun equals(other: Any?) = this === other
}

object LongType : Integral {
    override val bitsize: Int
        get() = Type.DWORD

    override val name = "long"
    override val width = 64
    override val signed = true
    override val isLong get() = true
    override val isDWord get() = true
    override val asmDesc get() = "J"

    override fun toString() = name
    override fun hashCode() = simpleHash(width, signed)
    override fun equals(other: Any?) = this === other
}

object CharType : Integral {
    override val name = "char"
    override val width = 16
    override val signed = false
    override val isChar get() = true
    override val asmDesc get() = "C"

    override fun toString() = name
    override fun hashCode() = simpleHash(width, signed)
    override fun equals(other: Any?) = this === other
}