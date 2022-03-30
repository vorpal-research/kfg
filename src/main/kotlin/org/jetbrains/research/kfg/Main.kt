package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.container.asContainer
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.util.write
import org.jetbrains.research.kfg.visitor.ClassVisitor
import org.jetbrains.research.kfg.visitor.LoopAnalysis
import org.jetbrains.research.kfg.visitor.executePipeline
import org.jetbrains.research.kthelper.tryOrNull
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths


private class ClassWriter(override val cm: ClassManager, val loader: ClassLoader, val target: Path) : ClassVisitor {

    override fun cleanup() {}

    override fun visit(klass: Class) {
        val classFileName = target.resolve(Paths.get(klass.pkg.fileSystemPath, "${klass.name}.class")).toAbsolutePath()
        try {
            klass.write(cm, loader, classFileName)
        } catch (e: Throwable) {
            println("Failed to write $klass")
        }
    }
}

private class ClassChecker(override val cm: ClassManager, val loader: ClassLoader, val target: Path) : ClassVisitor {
    override fun cleanup() {}

    override fun visit(klass: Class) {
        try {
            val writeLoader = URLClassLoader(arrayOf(target.toUri().toURL()))
            writeLoader.loadClass(klass.canonicalDesc)
        } catch (e: Throwable) {
            println("Failed to load written class: $klass: ${e.message}")
        }
    }
}

fun main(args: Array<String>) {
    val cfg = tryOrNull { KfgConfigParser(args) } ?: return

    val jars = cfg.getStringValue("jar").split(System.getProperty("path.separator")).map { Paths.get(it).asContainer()!! }

    val classManager = ClassManager(
        KfgConfig(
            Flags.readAll,
            useCachingLoopManager = false,
            failOnError = false,
            verifyIR = true
        )
    )

    classManager.initialize(*jars.toTypedArray())

    val loader = URLClassLoader(jars.map { it.path.toUri().toURL() }.toTypedArray())
    val target = Paths.get("instrumented/")
    val writeTarget = Paths.get("written/")
    jars.forEach { jar -> jar.unpack(classManager, target, true, classManager.failOnError) }
    executePipeline(classManager, Package.defaultPackage) {
        +LoopAnalysis(classManager)
        +LoopSimplifier(classManager)
        +ClassWriter(classManager, loader, writeTarget)
        +ClassChecker(classManager, loader, writeTarget)
    }
    jars.forEach { jar -> jar.update(classManager, target) }
}