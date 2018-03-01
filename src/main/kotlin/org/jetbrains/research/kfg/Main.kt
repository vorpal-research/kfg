package org.jetbrains.research.kfg

import org.slf4j.LoggerFactory

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("org.jetbrains.research.kfg.Main")
    val jr = JarReader(args[0])
    jr.doit()
}