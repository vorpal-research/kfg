package org.vorpal.research.kfg.visitor

interface KfgProvider<T> {
    fun provide(): T
}