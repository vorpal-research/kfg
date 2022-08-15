package org.vorpal.research.kfg.type


sealed class Real : PrimaryType {
    override val isPrimary get() = true
    override val isReal get() = true

    override fun toString() = name
    override fun hashCode() = name.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return this.javaClass == other?.javaClass
    }
}

object FloatType : Real() {
    override val bitSize = Type.WORD
    override val name = "float"
    override val asmDesc = "F"

    override fun isSubtypeOf(other: Type) = when (other) {
        is FloatType -> true
        is DoubleType -> true
        else -> false
    }
}

object DoubleType : Real() {
    override val bitSize = Type.DWORD
    override val name = "double"
    override val isDWord = true
    override val asmDesc = "D"

    override fun isSubtypeOf(other: Type) = when (other) {
        is DoubleType -> true
        else -> false
    }
}
