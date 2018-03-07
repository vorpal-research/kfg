package org.jetbrains.research.kfg.type

interface Real : Type {
    override fun isPrimary() = true
    override fun isReal() = true
}

class FloatType : Real {
    override fun getName() = "float"
    override fun toString() = getName()
    override fun getAsmDesc() = "F"

    companion object {
        val instance = FloatType()
    }
}

class DoubleType : Real {
    override fun getName() = "double"
    override fun toString() = getName()
    override fun isDWord() = true
    override fun getAsmDesc() = "D"

    companion object {
        val instance = DoubleType()
    }
}
