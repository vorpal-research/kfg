package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.builder.MethodBuilder
import org.jetbrains.research.kfg.ir.Class
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode
import java.util.jar.JarEntry
import java.util.jar.JarFile

fun parseJar(name: String): Map<String, ClassNode> {
    val classes = mutableMapOf<String, ClassNode>()
    val jar = JarFile(name)
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

class ClassManager private constructor() {
    val classNodes = mutableMapOf<String, ClassNode>()
    val classes = mutableMapOf<ClassNode, Class>()

    private object Holder {
        val instance = ClassManager()
    }

    companion object {
        val instance: ClassManager by lazy { Holder.instance }
    }

    fun init(jar: String) = classNodes.putAll(parseJar(jar))

    fun get(name: String) = classNodes.getOrPut(name, {
        val cn = ClassNode()
        cn.name = name
        cn
    })

    fun add(cn: ClassNode) {
        classNodes[cn.name] = cn
    }

    fun get(cn: ClassNode) = classes.getOrPut(cn, { Class(cn.name) })
    fun build(cn: ClassNode): Class {
        val klass = get(cn)
        return if (klass.builded) klass else {
            if (cn.superName != null) klass.superClass = getByName(cn.superName)
            klass.modifiers = cn.access
            cn.fields.forEach { val f = klass.getField(it as FieldNode) }
            cn.methods.forEach {
                it as MethodNode
                val method = klass.getMethod(it)
                if (!method.isAbstract() && !method.builded) {
                    MethodBuilder(method, it).convert()
                }
                method.builded = true
            }
            klass.builded = true
            klass
        }
    }

    fun getByName(name: String): Class {
        val cn = get(name)
        return get(cn)
    }
}