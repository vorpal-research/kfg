package org.jetbrains.research.kfg.builder.cfg

import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.Parameter
import org.objectweb.asm.tree.*

class ClassBuilder(val `class`: Class, val cn: ClassNode) {
    private fun buildMethod(mn: MethodNode): Method {
        val method = `class`.getMethod(mn)

        if (!method.builded) {
            if (mn.parameters != null) {
                mn.parameters.withIndex().forEach { (indx, param) ->
                    param as ParameterNode
                    method.parameters.add(Parameter(param.name, method.argTypes[indx], param.access))
                }
            }

            method.run {
                addVisibleAnnotations(mn.visibleAnnotations as List<AnnotationNode>?)
                addInvisibleAnnotations(mn.invisibleAnnotations as List<AnnotationNode>?)
                addVisibleTypeAnnotations(mn.visibleTypeAnnotations as List<TypeAnnotationNode>?)
                addInvisibleTypeAnnotations(mn.invisibleTypeAnnotations as List<TypeAnnotationNode>?)
            }

            if (!method.isAbstract()) {
                MethodBuilder(method, mn).build()
            }
            method.builded = true
        }
        return method
    }

    fun build(): Class {
        if (!`class`.builded) {
            `class`.run {
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
                cn.methods.forEach { buildMethod(it as MethodNode) }
                builded = true
            }
        }
        return `class`
    }
}