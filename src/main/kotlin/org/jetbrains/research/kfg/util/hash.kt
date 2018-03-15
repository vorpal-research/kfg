package org.jetbrains.research.kfg.util

fun defaultHasCode(vararg objects: Any): Int {
    var result = 1
    for (`object` in objects) {
        result = 31 * result + `object`.hashCode()
    }
    return result
}