package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.type.Type

class Parameter(cm: ClassManager, val indx: Int, name: String, val type: Type, modifiers: Int) : Node(cm, name, modifiers) {
    override val asmDesc = type.asmDesc
}