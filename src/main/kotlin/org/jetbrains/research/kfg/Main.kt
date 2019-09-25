package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.util.updateJar
import org.jetbrains.research.kfg.visitor.executePipeline
import java.util.jar.JarFile

fun main(args: Array<String>) {
    val cfg = KfgConfig(args)

    val jar = JarFile(cfg.getStringValue("jar"))
    val `package` = Package(cfg.getStringValue("package", "*"))

    val cm = ClassManager(jar, `package`, Flags.readAll)
    executePipeline(cm, `package`) {
        +LoopSimplifier(cm)
    }
    updateJar(cm, jar, `package`)
}