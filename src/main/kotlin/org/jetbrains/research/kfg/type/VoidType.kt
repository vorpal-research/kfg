package org.jetbrains.research.kfg.type

class VoidType : Type {
    companion object {
        val instance = VoidType()
    }

    override fun getName() = "void"
    override fun isPrimitive() = false
}