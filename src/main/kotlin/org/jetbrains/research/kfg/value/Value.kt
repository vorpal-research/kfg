package org.jetbrains.research.kfg.value

import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory

abstract class Value(val type: Type) {
    val TF = TypeFactory.instance

    abstract fun getName() : String
    override fun toString() = getName()
}