package org.jetbrains.research.kfg.ir.instruction

import org.jetbrains.research.kfg.value.Value

class EnterMonitorInst(owner: Value) : Instruction(arrayOf(owner))