package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.util.updateJar
import java.util.jar.JarFile

fun main(args: Array<String>) {
    val cfg = KfgConfig(args)

    val jar = JarFile(cfg.getStringValue("jar"))
    val `package` = Package(cfg.getStringValue("package", "*"))

    val cm = ClassManager(jar, `package`, Flags.readAll)
    updateJar(cm, jar, `package`)
}