package org.vorpal.research.kfg.ir

import org.vorpal.research.kfg.Package

data class Location(val pkg: Package, val file: String, val line: Int) {
    companion object {
        val UNKNOWN_PACKAGE = Package.defaultPackage
        const val UNKNOWN_SOURCE = "unknown"
        const val UNKNOWN_LINE = -1
    }

    constructor() : this(UNKNOWN_PACKAGE, UNKNOWN_SOURCE, UNKNOWN_LINE)

    val isKnown
        get() = pkg != UNKNOWN_PACKAGE && file != UNKNOWN_SOURCE && line != UNKNOWN_LINE

    override fun toString() = when {
        isKnown -> "$pkg/$file:$line"
        else -> UNKNOWN_SOURCE
    }
}
