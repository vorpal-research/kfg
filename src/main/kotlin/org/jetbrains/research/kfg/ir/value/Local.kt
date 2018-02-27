package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.type.Type

class Local(val indx: Int, type: Type) : Value("%$indx", type)