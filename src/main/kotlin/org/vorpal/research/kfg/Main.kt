package org.vorpal.research.kfg

import org.vorpal.research.kfg.container.asContainer
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.util.Flags
import org.vorpal.research.kfg.util.write
import org.vorpal.research.kfg.visitor.*
import org.vorpal.research.kthelper.tryOrNull
import java.net.URLClassLoader
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.measureTimeMillis

private class ClassWriter(override val cm: ClassManager, val loader: ClassLoader, val target: Path) : StandaloneClassVisitor {
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

private class ClassChecker(override val cm: ClassManager, val loader: ClassLoader, val target: Path) : StandaloneClassVisitor {
    override fun cleanup() {}

    override fun visit(klass: Class) {
        try {
            val writeLoader = URLClassLoader(arrayOf(target.toUri().toURL()))
            writeLoader.loadClass(klass.canonicalDesc)
        } catch (e: Throwable) {
            println("Failed to load written class: $klass: ${e.message ?: e}")
        }
    }
}

private class MethodBuilder(
    override val cm: ClassManager
) : MethodVisitor {
    override fun cleanup() {}

    override fun visit(method: Method) {
        method.body
    }
}

fun main(args: Array<String>) {
    val cfg = tryOrNull { KfgConfigParser(args) } ?: return

    val jars =
        cfg.getStringValue("jar").split(System.getProperty("path.separator")).map { Paths.get(it).asContainer()!! }

    val classManager = ClassManager(
        KfgConfig(
            Flags.readAll,
            useCachingLoopManager = false,
            failOnError = false,
            verifyIR = false,
            checkClasses = false
        )
    )

    val time = measureTimeMillis {
        classManager.initialize(*jars.toTypedArray())
    }
    println(time)

    val loader = URLClassLoader(jars.map { it.path.toUri().toURL() }.toTypedArray())
    val target = Paths.get("instrumented/")
    val writeTarget = Paths.get("written/")
    jars.forEach { jar -> jar.unpack(classManager, target, true, classManager.failOnError) }
    executePipeline(classManager, Package.defaultPackage) {
        +LoopSimplifier(classManager, this@executePipeline)
        +MethodBuilder(classManager)
        +ClassWriter(classManager, loader, writeTarget)
        +ClassChecker(classManager, loader, writeTarget)
    }
    jars.forEach { jar -> jar.update(classManager, target) }


    jars.forEach { jar ->
        jar.extract(Paths.get("extracted").also {
            it.toFile().mkdirs()
        })
    }
}