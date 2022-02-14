package org.jetbrains.research.kfg.visitor

interface KfgProvider<T> {
    fun provide(): T
}