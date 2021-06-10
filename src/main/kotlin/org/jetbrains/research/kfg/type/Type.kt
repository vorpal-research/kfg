package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.Package

interface Type {
    companion object {
        const val WORD = 32
        const val DWORD = 64
    }

    val name: String

    val asmDesc: String

    val isPrimary: Boolean

    val isDWord
        get() = false

    val isVoid
        get() = false

    val isIntegral
        get() = false

    val isReal
        get() = false

    val isReference
        get() = false

    val canonicalDesc
        get() = asmDesc.replace(Package.SEPARATOR, Package.CANONICAL_SEPARATOR)

    val bitSize: Int

    val isConcrete: Boolean
    fun isSubtypeOf(other: Type): Boolean
    fun isSupertypeOf(other: Type): Boolean = other.isSubtypeOf(this)
}