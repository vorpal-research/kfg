package org.jetbrains.research.kfg.type

interface Real : Type {
    override fun isPrimitive() = true
}

class FloatType : Real {
    override fun getName() = "float"
    override fun toString() = getName()

    companion object {
        val instance = FloatType()
    }
}

class DoubleType : Real {
    override fun getName() = "double"
    override fun toString() = getName()

    companion object {
        val instance = DoubleType()
    }
}
