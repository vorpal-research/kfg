package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.container.asContainer
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.visitor.LoopAnalysis
import org.jetbrains.research.kfg.visitor.executePipeline
import org.jetbrains.research.kthelper.tryOrNull
import java.nio.file.Paths

fun main(args: Array<String>) {
    val cfg = tryOrNull { KfgConfigParser(args) } ?: return

    val jars = cfg.getStringValue("jar").split(System.getProperty("path.separator")).map { Paths.get(it).asContainer()!! }

    val classManager = ClassManager(KfgConfig(Flags.readAll, false, verifyIR = true))

    classManager.initialize(*jars.toTypedArray())

    println(classManager.concreteClasses.joinToString("\n") { it.fullName })
    val target = Paths.get("instrumented/")
    jars.forEach { jar -> println(jar.commonPackage) }
    jars.forEach { jar -> jar.unpack(classManager, target, true, classManager.failOnError) }
    executePipeline(classManager, Package.defaultPackage) {
        +LoopAnalysis(classManager)
        +LoopSimplifier(classManager)
    }
    jars.forEach { jar -> jar.update(classManager, target) }
}