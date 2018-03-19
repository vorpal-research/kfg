package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.util.defaultHasCode

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

class BoolType : Integral {
    companion object {
        val instance = BoolType()
        const val defaultWidth = 32
        const val isSigned = false
    }

    override val name = "bool"
    override val width = defaultWidth
    override val signed = isSigned

    override fun getAsmDesc() = "Z"

    override fun toString() = name
    override fun hashCode() = defaultHasCode(width, signed)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return this.javaClass != other?.javaClass
    }
}

class ByteType : Integral {
    companion object {
        val instance = BoolType()
        const val defaultWidth = 8
        const val isSigned = true
    }

    override val name = "byte"
    override val width = defaultWidth
    override val signed = isSigned
    override fun isByte() = true
    override fun getAsmDesc() = "B"

    override fun toString() = name
    override fun hashCode() = defaultHasCode(width, signed)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return this.javaClass != other?.javaClass
    }
}

class ShortType : Integral {
    companion object {
        val instance = ShortType()
        const val defaultWidth = 16
        const val isSigned = true
    }
    override val name = "short"
    override val width = defaultWidth
    override val signed = isSigned
    override fun isShort() = true
    override fun getAsmDesc() = "S"

    override fun toString() = name
    override fun hashCode() = defaultHasCode(width, signed)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return this.javaClass != other?.javaClass
    }
}

class IntType : Integral {
    companion object {
        val instance = IntType()
        const val defaultWidth = 32
        const val isSigned = true
    }
    override val name = "int"
    override val width = defaultWidth
    override val signed = isSigned
    override fun isInt() = true
    override fun getAsmDesc() = "I"

    override fun toString() = name
    override fun hashCode() = defaultHasCode(width, signed)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return this.javaClass != other?.javaClass
    }
}

class LongType : Integral {
    companion object {
        val instance = LongType()
        const val defaultWidth = 64
        const val isSigned = true
    }
    override val name = "long"
    override val width = defaultWidth
    override val signed = isSigned
    override fun isLong() = true
    override fun isDWord() = true
    override fun getAsmDesc() = "J"

    override fun toString() = name
    override fun hashCode() = defaultHasCode(width, signed)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return this.javaClass != other?.javaClass
    }
}

class CharType : Integral {
    companion object {
        val instance = CharType()
        const val defaultWidth = 16
        const val isSigned = false
    }
    override val name = "char"
    override val width = defaultWidth
    override val signed = isSigned
    override fun isChar() = true
    override fun getAsmDesc() = "C"

    override fun toString() = name
    override fun hashCode() = defaultHasCode(width, signed)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return this.javaClass != other?.javaClass
    }
}