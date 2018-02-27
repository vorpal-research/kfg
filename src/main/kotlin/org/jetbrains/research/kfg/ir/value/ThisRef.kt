package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.type.Type

class ThisRef(type: Type) : Value("this", type)