package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.util.defaultHasCode

class VoidType : Type {
    companion object {
        val instance = VoidType()
    }

    override val name = "void"
    override fun isPrimary() = false
    override fun isVoid() = true
    override fun getAsmDesc() = "V"

    override fun hashCode() = defaultHasCode(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return this.javaClass != other?.javaClass
    }
}