package org.jetbrains.research.kfg.type

import org.jetbrains.research.kex.util.defaultHashCode

object VoidType : Type {
    override val name = "void"
    override fun isPrimary() = false
    override fun isVoid() = true
    override fun getAsmDesc() = "V"

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return this.javaClass != other?.javaClass
    }
}