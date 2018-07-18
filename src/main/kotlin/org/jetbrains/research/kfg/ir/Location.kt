package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.util.simpleHash

class Location(val `package`: Package, val file: String, val line: Int) {
    companion object {
        val UNKNOWN_PACKAGE = Package("*")
        const val UNKNOWN_SOURCE = "unknown"
        const val UNKNOWN_LINE = -1
    }

    constructor() : this(UNKNOWN_PACKAGE, UNKNOWN_SOURCE, UNKNOWN_LINE)

    fun isKnown() = (`package` != UNKNOWN_PACKAGE) and (file != UNKNOWN_SOURCE) and (line != UNKNOWN_LINE)

    override fun toString() = when {
        isKnown() -> "$`package`.$file:$line"
        else -> UNKNOWN_SOURCE
    }
    override fun hashCode() = simpleHash(`package`, file, line)
    override fun equals(other: Any?) = when {
        this === other -> true
        other == null -> false
        other is Location -> `package` == other.`package` && file == other.file && line == other.line
        else -> false
    }
}
