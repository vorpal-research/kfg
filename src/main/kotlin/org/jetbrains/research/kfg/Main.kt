package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.builder.ClassBuilder
import org.jetbrains.research.kfg.ir.ClassManager
import org.objectweb.asm.tree.MethodNode

fun main(args: Array<String>) {
    val jr = JarReader(args[0])
    jr.doit()

    val cm = ClassManager.instance
    for (it in jr.classes) {
        ClassBuilder(it.value).doit()
        val klass = cm.getClassByName(it.key)!!
        for (mn in it.value.methods as MutableList<MethodNode>) {
            println("Method ${mn.name}")
            println(printBytecode(mn))
            println(klass.getMethod(mn.name, mn.desc).print())
            println()
        }
    }
}