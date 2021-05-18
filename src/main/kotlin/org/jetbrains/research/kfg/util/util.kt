package org.jetbrains.research.kfg.util

import kotlin.math.min

private infix fun String.lcp(other: String): String {
    var indx = 0
    val max = min(this.length, other.length)
    while (indx < max) {
        if (this[indx] != other[indx]) break
        ++indx
    }
    return this.substring(0, indx)
}

// very bad implementation
internal fun longestCommonPrefix(strings: List<String>): String {
    if (strings.isEmpty()) return ""
    if (strings.size == 1) return strings.first()
    var prefix = strings[0] lcp strings[1]
    for (i in 2 until strings.size) {
        prefix = prefix lcp strings[i]
    }
    return prefix
}