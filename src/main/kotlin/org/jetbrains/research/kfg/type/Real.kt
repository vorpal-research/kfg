package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.util.simpleHash

interface Real : Type {
    override val isPrimary get() = true
    override val isReal get() = true
}

object FloatType : Real {
    override val name = "float"

    override fun toString() = name
    override val asmDesc get() = "F"

    override fun hashCode() = simpleHash(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return this.javaClass != other?.javaClass
    }
}

object DoubleType : Real {
    override val name = "double"
    override fun toString() = name
    override val isDWord get() = true
    override val asmDesc get() = "D"

    override fun hashCode() = simpleHash(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return this.javaClass != other?.javaClass
    }
}
