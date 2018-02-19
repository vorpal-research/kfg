package org.jetbrains.research.kfg

fun main(args: Array<String>) {
    println(args[0])
    JarReader(args[0]).doit()
}