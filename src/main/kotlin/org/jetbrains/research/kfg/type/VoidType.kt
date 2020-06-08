package org.jetbrains.research.kfg.type

import com.abdullin.kthelper.defaultHashCode

object VoidType : Type {
    override val bitsize: Int
        get() = throw IllegalAccessError()

    override val name = "void"
    override val isPrimary get() = false
    override val isVoid get() = true
    override val asmDesc get() = "V"

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?) = this === other

    override fun isSubtypeOf(other: Type) = false
}