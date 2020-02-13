package org.jetbrains.research.kfg.type

import com.abdullin.kthelper.util.defaultHashCode

interface Real : Type {
    override val isPrimary get() = true
    override val isReal get() = true
}

object FloatType : Real {
    override val bitsize = Type.WORD
    override val name = "float"
    override val asmDesc = "F"

    override fun toString() = name
    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return this.javaClass != other?.javaClass
    }
}

object DoubleType : Real {
    override val bitsize = Type.DWORD
    override val name = "double"
    override val isDWord = true
    override val asmDesc = "D"

    override fun toString() = name
    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return this.javaClass != other?.javaClass
    }
}
