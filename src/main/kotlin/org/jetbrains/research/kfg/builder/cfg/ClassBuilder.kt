package org.jetbrains.research.kfg.builder.cfg

import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.Parameter
import org.objectweb.asm.tree.*

class ClassBuilder(val `class`: Class, val cn: ClassNode) {
    fun build(): Class {
        if (!`class`.builded) {
            `class`.run {
                version = cn.version
                if (cn.superName != null) superClass = CM.getByName(cn.superName)
                modifiers = cn.access

                if (cn.interfaces != null) cn.interfaces.forEach {
                    it as String
                    val `interface` = CM.getByName(it)
                    `class`.interfaces[it] = `interface`
                }

                addVisibleAnnotations(cn.visibleAnnotations as List<AnnotationNode>?)
                addInvisibleAnnotations(cn.invisibleAnnotations as List<AnnotationNode>?)
                addVisibleTypeAnnotations(cn.visibleTypeAnnotations as List<TypeAnnotationNode>?)
                addInvisibleTypeAnnotations(cn.invisibleTypeAnnotations as List<TypeAnnotationNode>?)

                cn.fields.forEach { getField(it as FieldNode) }
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