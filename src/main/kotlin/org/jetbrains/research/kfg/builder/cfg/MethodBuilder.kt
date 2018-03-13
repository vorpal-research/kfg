package org.jetbrains.research.kfg.builder.cfg

import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.Parameter
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.ParameterNode
import org.objectweb.asm.tree.TypeAnnotationNode

class MethodBuilder(val method: Method, val mn: MethodNode) {
    fun build(): Method {
        method.run {
            if (!builded) {
                signature = mn.signature
                if (mn.parameters != null) {
                    mn.parameters.withIndex().forEach { (indx, param) ->
                        param as ParameterNode
                        parameters.add(Parameter(indx, param.name, method.argTypes[indx], param.access))
                    }
                }

                mn.exceptions.forEach { exceptions.add(CM.getByName(it as String)) }

                addVisibleAnnotations(mn.visibleAnnotations as List<AnnotationNode>?)
                addInvisibleAnnotations(mn.invisibleAnnotations as List<AnnotationNode>?)
                addVisibleTypeAnnotations(mn.visibleTypeAnnotations as List<TypeAnnotationNode>?)
                addInvisibleTypeAnnotations(mn.invisibleTypeAnnotations as List<TypeAnnotationNode>?)

                if (!isAbstract()) {
                    CfgBuilder(this, mn).build()
                }
                builded = true
            }
        }
        return method
    }
}