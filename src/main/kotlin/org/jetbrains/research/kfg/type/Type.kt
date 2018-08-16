package org.jetbrains.research.kfg.type

interface Type {
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
        get() = asmDesc.replace('/', '.')
}