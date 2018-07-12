package org.jetbrains.research.kfg.util

fun simpleHash(vararg objects: Any) = objects.fold(1) { acc, any -> 31 * acc + any.hashCode() }