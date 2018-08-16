package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.util.simpleHash

object VoidType : Type {
    override val name = "void"
    override val isPrimary get() = false
    override val isVoid get() = true
    override val asmDesc get() = "V"

    override fun hashCode() = simpleHash(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return this.javaClass != other?.javaClass
    }
}