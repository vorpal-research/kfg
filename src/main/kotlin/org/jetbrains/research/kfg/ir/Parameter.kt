package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.type.Type

class Parameter(name: String, val type: Type, modifiers: Int) : Node(name, modifiers)