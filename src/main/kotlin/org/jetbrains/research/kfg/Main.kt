package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.builder.asm.ClassBuilder
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.util.CheckClassAdapter
import java.io.FileOutputStream

fun main(args: Array<String>) {
    CM.parseJar(args[0])

    val keys = CM.classNodes.keys.toTypedArray()
    for (name in keys) {
        println("Visiting class $name")
        val `class` = CM.getByName(name)
        val cn = `class`.cn
        println("Class ${cn.name}")
        for ((_, method) in `class`.methods) {
            println("Visiting method ${method.name}")
            println("Bytecode: ")
            println(method.mn.printBytecode())
            println(method.print())
            println()
        }

        val cb = ClassBuilder(`class`)
        cb.visit()
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        val cca = CheckClassAdapter(cw)
        cb.cn.accept(cca)

        val realName = cb.cn.name.removeSuffix(".class").replace("/", ".")
        val fos = FileOutputStream("${realName.split('.').last()}.class")
        fos.write(cw.toByteArray())
        fos.close()
        println()
    }
}