package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory

abstract class Value(val name: String, val type: Type) {
    val TF = TypeFactory.instance

    override fun toString() = name
}