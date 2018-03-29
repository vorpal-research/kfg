package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.util.writeJar
import java.io.File
import java.util.jar.JarFile

fun main(args: Array<String>) {
    val cfg = KfgConfig(args)

    val jar = JarFile(cfg.getStringValue("jar"))
    val `package` = Package(cfg.getStringValue("package", "*"))
    val target = File(cfg.getStringValue("target", "instrumented/"))

    CM.parseJar(jar, `package`)
    writeJar(jar, `package`, target)
}