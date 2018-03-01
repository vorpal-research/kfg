package org.jetbrains.research.kfg

import org.objectweb.asm.tree.MethodNode

fun main(args: Array<String>) {
    CM.init(args[0])

    val keys = CM.classNodes.keys.toTypedArray()
    for (name in keys) {
        println("Visiting class $name")
        val cn = CM.get(name)
        val klass = CM.build(cn)
        for (mn in cn.methods as MutableList<MethodNode>) {
            println("Visiting method ${mn.name}")
            println("Bytecode: ")
            println(mn.printBytecode())
            println(klass.getMethod(mn.name, mn.desc).print())
            println()
        }
        println()
    }
}