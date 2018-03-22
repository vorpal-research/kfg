package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.builder.cfg.CfgBuilder
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.ir.OuterClass
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.*
import java.util.jar.JarEntry
import java.util.jar.JarFile

fun parseJar(jar: JarFile): Map<String, ClassNode> {
    val classes = mutableMapOf<String, ClassNode>()
    val enumeration = jar.entries()
    while (enumeration.hasMoreElements()) {
        val entry = enumeration.nextElement() as JarEntry

        if (entry.name.endsWith(".class")) {
            val classReader = ClassReader(jar.getInputStream(entry))
            val classNode = ClassNode()
            classReader.accept(classNode, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
            classes[classNode.name] = classNode
        }

    }
    jar.close()
    return classes
}

object ClassManager {
    val classToJar = mutableMapOf<String, JarFile>()
    val classNodes = mutableMapOf<String, ClassNode>()
    val classes = mutableMapOf<String, Class>()

    fun parseJar(jarPath: String) {
        val jar = JarFile(jarPath)
        val jarClasses = parseJar(jar)
        classNodes.putAll(jarClasses)
        classToJar.putAll(jarClasses.map { it.key to jar })
        jarClasses.forEach { (name, cn) ->
            classes.getOrPut(name, { ConcreteClass(cn) }).init()
        }
        classes.values.forEach {
            it.methods.forEach { _, method ->
                if (!method.isAbstract()) CfgBuilder(method).build()
            }
        }
    }

    fun get(cn: ClassNode) = classes.getOrPut(cn.name, { ConcreteClass(cn) })

    fun getByName(name: String): Class {
        var cn = classNodes[name]
        return if (cn != null) get(cn) else {
            cn = ClassNode()
            cn.name = name
            OuterClass(cn)
        }
    }
}