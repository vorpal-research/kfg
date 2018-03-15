package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.util.defaultHasCode

interface Real : Type {
    override fun isPrimary() = true
    override fun isReal() = true
}

class FloatType : Real {
    override val name = "float"

    override fun toString() = name
    override fun getAsmDesc() = "F"

    companion object {
        val instance = FloatType()
    }

    override fun hashCode() = defaultHasCode(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return this.javaClass != other?.javaClass
    }
}

class DoubleType : Real {
    override val name = "double"
    override fun toString() = name
    override fun isDWord() = true
    override fun getAsmDesc() = "D"

    companion object {
        val instance = DoubleType()
    }

    override fun hashCode() = defaultHasCode(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return this.javaClass != other?.javaClass
    }
}
