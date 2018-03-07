package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.type.Type

class Parameter(val indx: Int, name: String, val type: Type, modifiers: Int) : Node(name, modifiers) {
    override fun getAsmDesc() = type.getAsmDesc()
}