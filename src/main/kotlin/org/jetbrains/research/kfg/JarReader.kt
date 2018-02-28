package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.builder.ClassBuilder
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.*
import java.util.jar.JarFile
import java.util.jar.JarEntry
import java.util.HashMap

class JarReader(val name: String) {
    val jar = JarFile(name)
    val classes = mutableMapOf<String, ClassNode>()

    init {
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
        } catch (ex: Exception) {
        }
    }

    fun doit() {
        for (it in classes) {
            ClassBuilder(it.value).doit()
        }
    }
}