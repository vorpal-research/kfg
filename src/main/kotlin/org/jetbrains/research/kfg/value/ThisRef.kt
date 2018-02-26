package org.jetbrains.research.kfg.value

import org.jetbrains.research.kfg.type.Type

class ThisRef(type: Type) : Value(type) {
    override fun getName() = "this"
}