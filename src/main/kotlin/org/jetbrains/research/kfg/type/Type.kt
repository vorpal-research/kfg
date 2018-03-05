package org.jetbrains.research.kfg.type

interface Type {
    fun getName() : String
    fun isPrimitive() : Boolean
    fun isDWord() = false
    fun isVoid() = false
    fun isIntegral() = false
    fun isReal() = false
    fun isReference() = false
}