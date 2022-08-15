package org.vorpal.research.kfg.type

object VoidType : Type {
    override val bitSize: Int
        get() = throw IllegalAccessError()

    override val name = "void"
    override val isPrimary get() = false
    override val isVoid get() = true
    override val asmDesc get() = "V"

    override fun hashCode() = name.hashCode()
    override fun equals(other: Any?) = this === other
    override fun toString(): String = name

    override val isConcrete get() = true
    override fun isSubtypeOf(other: Type) = false
}