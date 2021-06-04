package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.container.asContainer
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kthelper.logging.log
import java.io.File

fun main(args: Array<String>) {
    val cfg = KfgConfigParser(args)

    val jar = File(cfg.getStringValue("jar")).asContainer()!!

    val classManager = ClassManager(KfgConfig(Flags.readAll, false))
    log.error("aaaaaaaaaaaaaaa")

    classManager.initialize(jar)
    log.error("aaaaaaaaaaaaaaa")

    log.debug(classManager.concreteClasses.joinToString("\n") { it.fullName })
//    val target = File("instrumented/")
//    println(jar.commonPackage)
//    jar.unpack(classManager, target.toPath(), true, classManager.failOnError)
//    executePipeline(classManager, jar.pkg) {
//        +LoopAnalysis(classManager)
//        +LoopSimplifier(classManager)
//    }
//    jar.update(classManager)
}