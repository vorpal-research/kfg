package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.visitor.executePipeline
import java.io.File

fun main(args: Array<String>) {
    val cfg = KfgConfigParser(args)

    val jar = Jar(cfg.getStringValue("jar"), cfg.getStringValue("package", "*"))

    val classManager = ClassManager(KfgConfig(Flags.readAll, false))
    classManager.initialize(jar)
    val target = File("instrumented/")
    jar.unpack(classManager, target.toPath(), true)
    executePipeline(classManager, jar.`package`) {
        +LoopSimplifier(classManager)
    }
    jar.update(classManager)
}