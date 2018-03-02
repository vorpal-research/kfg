package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.builder.MethodBuilder
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Parameter
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.*
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

    fun add(cn: ClassNode) {
        classNodes[cn.name] = cn
    }

    fun get(name: String) = classNodes.getOrPut(name, {
        val cn = ClassNode()
        cn.name = name
        cn
    })

    fun get(cn: ClassNode) = classes.getOrPut(cn, { Class(cn.name) })

    fun getByName(name: String): Class {
        val cn = get(name)
        return get(cn)
    }

    fun build(cn: ClassNode): Class {
        val `class` = get(cn)
        if (!`class`.builded) {
            `class`.run {
                if (cn.superName != null) superClass = getByName(cn.superName)
                modifiers = cn.access

                if (cn.interfaces != null) cn.interfaces.forEach {
                    it as String
                    val `interface` = getByName(it)
                    `class`.interfaces[it] = `interface`
                }

                addVisibleAnnotations(cn.visibleAnnotations as List<AnnotationNode>?)
                addInvisibleAnnotations(cn.invisibleAnnotations as List<AnnotationNode>?)
                addVisibleTypeAnnotations(cn.visibleTypeAnnotations as List<TypeAnnotationNode>?)
                addInvisibleTypeAnnotations(cn.invisibleTypeAnnotations as List<TypeAnnotationNode>?)

                cn.fields.forEach { getField(it as FieldNode) }
                cn.methods.forEach {
                    it as MethodNode
                    val method = getMethod(it)

                    if (it.parameters != null) {
                        it.parameters.withIndex().forEach { (indx, param) ->
                            param as ParameterNode
                            method.parameters.add(Parameter(param.name, method.argTypes[indx], param.access))
                        }
                    }

                    method.run {
                        addVisibleAnnotations(it.visibleAnnotations as List<AnnotationNode>?)
                        addInvisibleAnnotations(it.invisibleAnnotations as List<AnnotationNode>?)
                        addVisibleTypeAnnotations(it.visibleTypeAnnotations as List<TypeAnnotationNode>?)
                        addInvisibleTypeAnnotations(it.invisibleTypeAnnotations as List<TypeAnnotationNode>?)
                    }

                    if (!method.isAbstract() && !method.builded) {
                        MethodBuilder(method, it).build()
                    }
                    method.builded = true
                }
                builded = true
            }
        }
        return `class`
    }
}