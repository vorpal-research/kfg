package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.Package

data class Location(val `package`: Package, val file: String, val line: Int) {
    companion object {
        val UNKNOWN_PACKAGE = Package("*")
        const val UNKNOWN_SOURCE = "unknown"
        const val UNKNOWN_LINE = -1
    }

    constructor() : this(UNKNOWN_PACKAGE, UNKNOWN_SOURCE, UNKNOWN_LINE)

    val isKnown
        get() = `package` != UNKNOWN_PACKAGE && file != UNKNOWN_SOURCE && line != UNKNOWN_LINE

    override fun toString() = when {
        isKnown -> "$`package`/$file:$line"
        else -> UNKNOWN_SOURCE
    }
}
