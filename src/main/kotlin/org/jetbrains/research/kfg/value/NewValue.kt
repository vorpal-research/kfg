package org.jetbrains.research.kfg.value

import org.jetbrains.research.kfg.type.Type

class NewValue(type: Type) : Value(type, arrayOf()) {
    override fun getName() = "new $type"
}