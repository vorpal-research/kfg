package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.container.asContainer
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.visitor.LoopAnalysis
import org.jetbrains.research.kfg.visitor.executePipeline
import org.jetbrains.research.kthelper.tryOrNull
import java.io.File
import java.nio.file.Paths

fun main(args: Array<String>) {
    val cfg = tryOrNull { KfgConfigParser(args) } ?: return

    val jar = File(cfg.getStringValue("jar")).asContainer()!!

    val classManager = ClassManager(KfgConfig(Flags.readAll, true, verifyIR = true))

    classManager.initialize(jar)

    println(classManager.concreteClasses.joinToString("\n") { it.fullName })
    val target = Paths.get("instrumented/")
    println(jar.commonPackage)
    jar.unpack(classManager, target, true, classManager.failOnError)
    executePipeline(classManager, jar.pkg) {
        +LoopAnalysis(classManager)
        +LoopSimplifier(classManager)
    }
    jar.update(classManager, target)
}