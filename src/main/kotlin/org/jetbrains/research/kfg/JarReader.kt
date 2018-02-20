package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.builder.ClassBuilder
import org.jetbrains.research.kfg.ir.ClassManager
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.*
import java.net.URL
import java.util.jar.JarFile
import java.util.jar.JarEntry
import java.util.HashMap

class JarReader(val name: String) {
    val jar = JarFile(name)

    fun parseJar(jar: JarFile): Map<String, ClassNode> {
        val classes = HashMap<String, ClassNode>()

        try {
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
        } catch (ex: Exception) {
            return mutableMapOf()
        }
    }

    fun doit() {
        val entries = jar.entries()
        for (it in parseJar(jar)) {
            ClassBuilder(it.value).doit()
        }
        ClassManager.instance.classes.forEach {
            println("${it.value.packageName} ${it.value.name}")
        }
    }
}