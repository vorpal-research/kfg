package org.vorpal.research.kfg.builder.asm

import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InnerClassNode
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.AnnotationBase
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Field
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.visitor.ClassVisitor

class ClassBuilder(override val cm: ClassManager, val `class`: Class) : ClassVisitor {
    override fun cleanup() {}

    override fun visit(klass: Class) {
        val cn = klass.cn
        cn.access = klass.modifiers.value
        cn.superName = klass.superClass?.fullName
        cn.interfaces.clear()
        cn.interfaces.addAll(klass.interfaces.map { it.fullName })
        cn.outerClass = klass.outerClass?.fullName
        cn.outerMethod = klass.outerMethod?.name
        cn.outerMethodDesc = klass.outerMethod?.desc?.asmDesc
        cn.innerClasses.clear()
        cn.innerClasses.addAll(klass.innerClasses.map { (klass, modifiers) ->
            InnerClassNode(klass.fullName, klass.outerClass?.fullName, klass.name, modifiers.value)
        })
        cn.fields = klass.fields.map { it.fn }
        cn.methods = klass.allMethods.map { it.mn }
        cn.visibleAnnotations = mutableListOf()
        cn.invisibleAnnotations = mutableListOf()
        cn.visibleTypeAnnotations = mutableListOf()
        cn.invisibleTypeAnnotations = mutableListOf()
        klass.annotations.forEach {
            val list = when {
                it.visible -> cn.visibleAnnotations
                else -> cn.invisibleAnnotations
            }
            list += AnnotationBase.toAnnotationNode(it)
        }
        klass.typeAnnotations.forEach {
            val list = when {
                it.visible -> cn.visibleTypeAnnotations
                else -> cn.invisibleTypeAnnotations
            }
            list += AnnotationBase.toAnnotationNode(it)
        }
        super.visit(klass)
    }

    override fun visitMethod(method: Method) {
        AsmBuilder(cm, method).build()
        // because sometimes ASM is not able to process kotlin-generated signatures
        method.mn.signature = null
    }

    override fun visitField(field: Field) {
        field.fn.value = cm.value.unwrapConstant(field.defaultValue)
        // because sometimes ASM is not able to process kotlin-generated signatures
        field.fn.signature = null
        field.fn.visibleAnnotations = mutableListOf()
        field.fn.invisibleAnnotations = mutableListOf()
        field.fn.visibleTypeAnnotations = mutableListOf()
        field.fn.invisibleTypeAnnotations = mutableListOf()
        field.annotations.forEach {
            val list = when {
                it.visible -> field.fn.visibleAnnotations
                else -> field.fn.invisibleAnnotations
            }
            list += AnnotationBase.toAnnotationNode(it)
        }
        field.typeAnnotations.forEach {
            val list = when {
                it.visible -> field.fn.visibleTypeAnnotations
                else -> field.fn.invisibleTypeAnnotations
            }
            list += AnnotationBase.toAnnotationNode(it)
        }
    }

    fun build(): ClassNode {
        visit(`class`)
        // because sometimes ASM is not able to process kotlin-generated signatures
        `class`.cn.signature = null
        return `class`.cn
    }

    operator fun invoke(): ClassNode = build()
}
