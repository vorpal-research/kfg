package org.jetbrains.research.kfg.builder.cfg

import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.UnexpectedException
import org.jetbrains.research.kfg.ir.Class
import org.objectweb.asm.tree.*

class ClassBuilder(val `class`: Class, val cn: ClassNode) {
    fun build(): Class {
        `class`.run {
            if (!builded) {
                version = cn.version
                signature = cn.signature
                if (cn.superName != null) superClass = CM.getByName(cn.superName)
                modifiers = cn.access

                if (cn.interfaces != null) cn.interfaces.forEach {
                    it as String
                    val `interface` = CM.getByName(it)
                    interfaces[it] = `interface`
                }
                if (cn.outerClass != null) {
                    val outer = CM.getByName(cn.outerClass)
                    outerClass = outer
                    outer.innerClasses.add(this)
                }
                if (cn.outerMethod != null) {
                    val outer = outerClass
                            ?: throw UnexpectedException("Class with outer method, nut no outer class $`class`")
                    outerMethod = outer.getMethod(cn.outerMethod, cn.outerMethodDesc)
                }
                for (it in cn.innerClasses as List<InnerClassNode>) {
                    val innerClass = CM.getByName(it.name)
                    innerClasses.add(innerClass)
                }

                addVisibleAnnotations(cn.visibleAnnotations as List<AnnotationNode>?)
                addInvisibleAnnotations(cn.invisibleAnnotations as List<AnnotationNode>?)
                addVisibleTypeAnnotations(cn.visibleTypeAnnotations as List<TypeAnnotationNode>?)
                addInvisibleTypeAnnotations(cn.invisibleTypeAnnotations as List<TypeAnnotationNode>?)

                cn.fields.forEach {
                    val field = getField(it as FieldNode)
                    field.signature = it.signature
                }
                cn.methods.forEach {
                    it as MethodNode
                    MethodBuilder(getMethod(it), it).build()
                }
                builded = true
            }
        }
        return `class`
    }
}