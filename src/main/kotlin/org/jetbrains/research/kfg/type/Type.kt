package org.jetbrains.research.kfg.type

interface Type {
    fun getName() : String
    fun isPrimitive() : Boolean
}