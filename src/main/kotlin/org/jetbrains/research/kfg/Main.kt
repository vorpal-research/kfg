package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.util.unpack
import org.jetbrains.research.kfg.util.update
import org.jetbrains.research.kfg.visitor.executePipeline
import java.io.File
import java.util.jar.JarFile

fun main(args: Array<String>) {
    val cfg = KfgConfigParser(args)

    val jar = JarFile(cfg.getStringValue("jar"))
    val `package` = Package(cfg.getStringValue("package", "*"))

    val cm = ClassManager(jar, KfgConfig(`package` = `package`, flags = Flags.readAll, failOnError = true))
    val target = File("instrumented/")
    jar.unpack(cm, target.toPath(), Package.defaultPackage, true)
    executePipeline(cm, `package`) {
        +LoopSimplifier(cm)
    }
    jar.update(cm, `package`)
}