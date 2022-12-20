package org.vorpal.research.kfg.type

import org.vorpal.research.kfg.Package

abstract class Type {
    companion object {
        const val WORD = 32
        const val DWORD = 64
    }

    abstract val name: String

    abstract val asmDesc: String

    abstract val isPrimitive: Boolean

    open val isDWord
        get() = false

    open val isVoid
        get() = false

    open val isInteger
        get() = false

    open val isReal
        get() = false

    open val isReference
        get() = false

    val canonicalDesc
        get() = asmDesc.replace(Package.SEPARATOR, Package.CANONICAL_SEPARATOR)

    abstract val bitSize: Int

    abstract val isConcrete: Boolean
    abstract fun isSubtypeOf(other: Type): Boolean
    fun isSupertypeOf(other: Type): Boolean = other.isSubtypeOf(this)

    val asArray: ArrayType by lazy {
        ArrayType(this)
    }
}
