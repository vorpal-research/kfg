package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.Type

class Argument(argName: String, val method: Method, type: Type) : Value(argName, type)