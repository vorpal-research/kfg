package org.jetbrains.research.kfg.type

class BoolType : Type {
    override fun getName() = "bool"
    override fun isPrimitive() = true
    override fun toString() = getName()

    companion object {
        val instance = BoolType()
    }
}