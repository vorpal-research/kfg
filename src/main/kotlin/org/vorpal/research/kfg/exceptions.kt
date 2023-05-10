package org.vorpal.research.kfg

abstract class KfgException : Exception {
    constructor() : super()
    constructor(msg: String) : super(msg)
    @Suppress("unused")
    constructor(msg: String, reason: Throwable) : super(msg, reason)
    constructor(reason: Throwable) : super(reason)

    override fun toString(): String = "${this.javaClass.kotlin} : $message"
}

@Deprecated("not used")
class InvalidTypeException(msg: String) : KfgException(msg)

class InvalidOpcodeException(msg: String) : KfgException(msg)

class InvalidOperandException(msg: String) : KfgException(msg)

class InvalidStateException(msg: String) : KfgException(msg)

class UnknownInstanceException(msg: String) : KfgException(msg)

class UnsupportedOperationException(msg: String) : KfgException(msg)

class UnsupportedCfgException(msg: String): KfgException(msg) {
    constructor() : this("")
}
