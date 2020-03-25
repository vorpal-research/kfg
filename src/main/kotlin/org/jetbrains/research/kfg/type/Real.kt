package org.jetbrains.research.kfg.type

import com.abdullin.kthelper.defaultHashCode

sealed class Real : PrimaryType {
    override val isPrimary get() = true
    override val isReal get() = true

    override fun toString() = FloatType.name
    override fun hashCode() = defaultHashCode(FloatType.name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return this.javaClass == other?.javaClass
    }
}

object FloatType : Real() {
    override val bitsize = Type.WORD
    override val name = "float"
    override val asmDesc = "F"

    override fun isSubtypeOf(other: Type) = when (other) {
        is FloatType -> true
        is DoubleType -> true
        else -> false
    }
}

object DoubleType : Real() {
    override val bitsize = Type.DWORD
    override val name = "double"
    override val isDWord = true
    override val asmDesc = "D"

    override fun isSubtypeOf(other: Type) = when (other) {
        is DoubleType -> true
        else -> false
    }
}
