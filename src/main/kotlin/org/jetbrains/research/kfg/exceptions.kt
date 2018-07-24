package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.ir.value.instruction.Instruction

abstract class KfgException(msg: String) : Exception(msg)

class InvalidAccessError(msg: String): KfgException(msg)

class InvalidCallError(msg: String) : KfgException(msg)

class InvalidTypeDescError(msg: String) : KfgException(msg)

class InvalidOpcodeError(msg: String) : KfgException(msg)

class InvalidOperandError(msg: String) : KfgException(msg)

class InvalidStateError(msg: String) : KfgException(msg)

class InvalidInstructionError(inst: Instruction) : KfgException(inst.print())

class UnknownInstance(msg: String) : KfgException(msg)

class UnsupportedOperation(msg: String) : KfgException(msg)