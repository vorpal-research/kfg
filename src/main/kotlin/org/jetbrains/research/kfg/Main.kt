package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.builder.asm.AsmBuilder
import org.jetbrains.research.kfg.builder.asm.ClassBuilder
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.util.printBytecode
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.util.CheckClassAdapter
import java.io.FileOutputStream

fun main(args: Array<String>) {
    CM.parseJar(args[0])

    val classes = CM.classes.values.filter { it is ConcreteClass }
    for (`class` in classes) {
        println("Visiting class $`class`")
        for ((_, method) in `class`.methods) {
            println("Visiting method $method")
            println("Bytecode: ")
            println(method.mn.printBytecode())
            println(method.print())
            if (method.name == "view") {
                println("Rebuilded:")
                for (it in AsmBuilder(method).build())
                    print((it as AbstractInsnNode).printBytecode())
            }
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