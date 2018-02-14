package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.value.Value

class Method(val name: String, val classRef: Class, val modifiers: Int, val arguments: Array<Type>, val retType: Type) {
    val locals = mapOf<Int, Value>()
    val basicBlocks = mutableListOf<BasicBlock>()
}