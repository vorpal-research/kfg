package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.util.updateJar
import org.jetbrains.research.kfg.util.writeClassesToTarget
import org.jetbrains.research.kfg.visitor.executePipeline
import java.io.File
import java.util.jar.JarFile

fun main(args: Array<String>) {
    val cfg = KfgConfigParser(args)

    val jar = JarFile(cfg.getStringValue("jar"))
    val `package` = Package(cfg.getStringValue("package", "*"))

    val cm = ClassManager(jar, KfgConfig(`package` = `package`, flags = Flags.readAll, failOnError = false))
    val target = File("instrumented/")
    writeClassesToTarget(cm, jar, target, Package.defaultPackage, true)
    executePipeline(cm, `package`) {
        +LoopSimplifier(cm)
    }
    updateJar(cm, jar, `package`)
}