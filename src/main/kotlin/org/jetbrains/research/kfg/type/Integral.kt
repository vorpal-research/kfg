package org.jetbrains.research.kfg.type

interface Integral : Type {
    override fun isPrimitive() = true
    override fun isIntegral() = true

    fun getWidth(): Int
    fun isSigned(): Boolean

    fun isByte() = false
    fun isShort() = false
    fun isInt() = false
    fun isLong() = false
    fun isChar() = false
}

class ByteType : Integral {
    companion object {
        val instance = ByteType()
        const val width = 8
        const val signed = true
    }
    override fun getName() = "byte"
    override fun getWidth() = width
    override fun isSigned() = signed
    override fun isByte() = true
}

class ShortType : Integral {
    companion object {
        val instance = ShortType()
        const val width = 16
        const val signed = true
    }
    override fun getName() = "short"
    override fun getWidth() = width
    override fun isSigned() = signed
    override fun isShort() = true
}

class IntType : Integral {
    companion object {
        val instance = IntType()
        const val width = 32
        const val signed = true
    }
    override fun getName() = "int"
    override fun getWidth() = width
    override fun isSigned() = signed
    override fun isInt() = true
}

class LongType : Integral {
    companion object {
        val instance = LongType()
        const val width = 64
        const val signed = true
    }
    override fun getName() = "long"
    override fun getWidth() = width
    override fun isSigned() = signed
    override fun isLong() = true
    override fun isDWord() = true
}

class CharType : Integral {
    companion object {
        val instance = CharType()
        const val width = 16
        const val signed = false
    }
    override fun getName() = "char"
    override fun getWidth() = width
    override fun isSigned() = signed
    override fun isChar() = true
}