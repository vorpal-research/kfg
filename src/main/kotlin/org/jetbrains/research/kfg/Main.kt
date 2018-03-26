package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.util.print
import org.jetbrains.research.kfg.util.writeJar
import java.util.jar.JarFile

fun main(args: Array<String>) {
    require(args.isNotEmpty(), { "Specify input jar file" })
    val jar = JarFile(args[0])

    val `package` = Package("org/jetbrains/research/kfg/*")
    CM.parseJar(jar, `package`)

    val classes = CM.classes.values.filter { it is ConcreteClass }
    for (`class` in classes) {
        println("Visiting class $`class`")
        for ((_, method) in `class`.methods) {
            println("Visiting method $method")
            println("Bytecode: ")
            println(method.mn.print())
            println(method.print())
            println()
        }
    }

    writeJar(jar, `package`, "instrumented")
}