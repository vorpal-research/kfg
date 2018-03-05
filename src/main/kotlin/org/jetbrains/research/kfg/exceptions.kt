package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.ir.value.instruction.Instruction

abstract class KfgException(msg: String) : Exception(msg)

class InvalidTypeDescException(msg: String) : KfgException(msg)
class UnexpectedOpcodeException(msg: String) : KfgException(msg)
class InvalidOperandException(msg: String) : KfgException(msg)
class UnexpectedException(msg: String) : KfgException(msg)

class UnknownInst(val inst: Instruction) : KfgException(inst.print())