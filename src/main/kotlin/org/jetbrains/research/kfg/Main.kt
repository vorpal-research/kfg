package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.builder.asm.ClassBuilder
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.util.CheckClassAdapter
import java.io.FileOutputStream

fun main(args: Array<String>) {
    CM.init(args[0])

    val keys = CM.classNodes.keys.toTypedArray()
    for (name in keys) {
        println("Visiting class $name")
        val cn = CM.get(name)
        val `class` = CM.getBuilded(cn)
        println("Class ${cn.name} signature ${cn.signature}")
        for (mn in cn.methods as MutableList<MethodNode>) {
            println("Visiting method ${mn.name} ${mn.signature}")
            println("Bytecode: ")
            println(mn.printBytecode())
            println(`class`.getMethod(mn.name, mn.desc).print())
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