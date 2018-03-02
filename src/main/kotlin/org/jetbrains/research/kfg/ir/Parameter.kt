package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.type.Type

class Parameter : Node {
    val type: Type

    constructor(name: String, type: Type, modifiers: Int) : super(name, modifiers) {
        this.type = type
    }
}