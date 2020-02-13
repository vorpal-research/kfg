package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.ir.value.instruction.Instruction

abstract class KfgException : Exception {
    constructor() : super()
    constructor(msg: String) : super(msg)
    constructor(msg: String, reason: Throwable) : super(msg, reason)
    constructor(reason: Throwable) : super(reason)
}

class InvalidAccessError(msg: String): KfgException(msg)

class InvalidCallError(msg: String) : KfgException(msg)

class InvalidTypeDescError(msg: String) : KfgException(msg)

class InvalidOpcodeError(msg: String) : KfgException(msg)

class InvalidOperandError(msg: String) : KfgException(msg)

class InvalidStateError(msg: String) : KfgException(msg)

class InvalidInstructionError(inst: Instruction) : KfgException(inst.print())

class UnknownInstance(msg: String) : KfgException(msg)

class UnsupportedOperation(msg: String) : KfgException(msg)

class UnsupportedCfgException(msg: String): KfgException(msg)

class UnknownTypeException(msg: String): KfgException(msg)