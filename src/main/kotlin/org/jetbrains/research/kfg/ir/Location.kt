package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.util.simpleHash

data class Location(val `package`: Package, val file: String, val line: Int) {
    companion object {
        val UNKNOWN_PACKAGE = Package("*")
        const val UNKNOWN_SOURCE = "unknown"
        const val UNKNOWN_LINE = -1
    }

    constructor() : this(UNKNOWN_PACKAGE, UNKNOWN_SOURCE, UNKNOWN_LINE)

    fun isKnown() = (`package` != UNKNOWN_PACKAGE) and (file != UNKNOWN_SOURCE) and (line != UNKNOWN_LINE)

    override fun toString() = when {
        isKnown() -> "$`package`/$file:$line"
        else -> UNKNOWN_SOURCE
    }
}
