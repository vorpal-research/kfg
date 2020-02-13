package org.jetbrains.research.kfg.type

import com.abdullin.kthelper.util.defaultHashCode

object VoidType : Type {
    override val bitsize: Int
        get() = throw IllegalAccessError()

    override val name = "void"
    override val isPrimary get() = false
    override val isVoid get() = true
    override val asmDesc get() = "V"

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return this.javaClass != other?.javaClass
    }
}