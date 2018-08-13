package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.util.simpleHash

interface Integral : Type {
    val width: Int
    val signed: Boolean

    override fun isPrimary() = true
    override fun isIntegral() = true

    fun isByte() = false
    fun isShort() = false
    fun isInt() = false
    fun isLong() = false
    fun isChar() = false
}

object BoolType : Integral {
    override val name = "bool"
    override val width = 32
    override val signed = false

    override fun getAsmDesc() = "Z"

    override fun toString() = name
    override fun hashCode() = simpleHash(width, signed)
    override fun equals(other: Any?) = this === other
}

object ByteType : Integral {
    override val name = "byte"
    override val width = 8
    override val signed = true
    override fun isByte() = true
    override fun getAsmDesc() = "B"

    override fun toString() = name
    override fun hashCode() = simpleHash(width, signed)
    override fun equals(other: Any?) = this === other
}

object ShortType : Integral {
    override val name = "short"
    override val width = 16
    override val signed = true
    override fun isShort() = true
    override fun getAsmDesc() = "S"

    override fun toString() = name
    override fun hashCode() = simpleHash(width, signed)
    override fun equals(other: Any?) = this === other
}

object IntType : Integral {
    override val name = "int"
    override val width = 32
    override val signed = true
    override fun isInt() = true
    override fun getAsmDesc() = "I"

    override fun toString() = name
    override fun hashCode() = simpleHash(width, signed)
    override fun equals(other: Any?) = this === other
}

object LongType : Integral {
    override val name = "long"
    override val width = 64
    override val signed = true
    override fun isLong() = true
    override fun isDWord() = true
    override fun getAsmDesc() = "J"

    override fun toString() = name
    override fun hashCode() = simpleHash(width, signed)
    override fun equals(other: Any?) = this === other
}

object CharType : Integral {
    override val name = "char"
    override val width = 16
    override val signed = false
    override fun isChar() = true
    override fun getAsmDesc() = "C"

    override fun toString() = name
    override fun hashCode() = simpleHash(width, signed)
    override fun equals(other: Any?) = this === other
}