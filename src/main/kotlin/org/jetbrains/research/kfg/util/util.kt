package org.jetbrains.research.kfg.util

// very bad implementation
internal fun longestCommonPrefix(strings: Collection<String>): String {
    if (strings.isEmpty()) return ""
    var indx = 0
    while (strings.map { it[indx] }.toSet().size == 1) {
        ++indx
    }
    return strings.first().take(indx)
}