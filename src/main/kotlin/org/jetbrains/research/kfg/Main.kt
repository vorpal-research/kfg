package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.builder.ClassBuilder
import org.jetbrains.research.kfg.ir.ClassManager
import org.objectweb.asm.tree.MethodNode
import org.slf4j.LoggerFactory

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("org.jetbrains.research.kfg.Main")
    val jr = JarReader(args[0])
    jr.doit()
//
//    val cm = ClassManager.instance
//    for (it in jr.classes) {
//        ClassBuilder(it.value).doit()
//        val klass = cm.getClassByName(it.key)!!
//        for (mn in it.value.methods as MutableList<MethodNode>) {
//            log.debug("Method ${mn.name}")
//            log.debug(printBytecode(mn))
//            log.debug(klass.getMethod(mn.name, mn.desc).print())
//            log.debug("")
//        }
//    }
}