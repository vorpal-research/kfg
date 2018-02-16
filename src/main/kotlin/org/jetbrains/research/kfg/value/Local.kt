package org.jetbrains.research.kfg.value

import org.jetbrains.research.kfg.type.Type

class Local(val indx: Int, type: Type) : Value(type) {
    override fun getName() = "%$indx"
}