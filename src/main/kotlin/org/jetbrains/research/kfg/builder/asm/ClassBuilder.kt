package org.jetbrains.research.kfg.builder.asm

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.visitor.ClassVisitor
import org.jetbrains.research.kfg.visitor.Pipeline
import org.jetbrains.research.kfg.visitor.pass.AnalysisVisitor
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InnerClassNode

class ClassBuilder(override val cm: ClassManager, val `class`: Class) : ClassVisitor {
    private val _pipeline = object : Pipeline(cm) {
        override fun runInternal() {
            // Do nothing
        }
    }
    override val pipeline: Pipeline get() = _pipeline

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
        super.visit(klass)
    }

    override fun visitMethod(method: Method) {
        if (method !in `class`.failingMethods)
            AsmBuilder(cm, method).build()
        // because sometimes ASM is not able to process kotlin-generated signatures
        method.mn.signature = null
    }

    override fun visitField(field: Field) {
        field.fn.value = cm.value.unwrapConstant(field.defaultValue)
        // because sometimes ASM is not able to process kotlin-generated signatures
        field.fn.signature = null
    }

    fun build(): ClassNode {
        visit(`class`)
        // because sometimes ASM is not able to process kotlin-generated signatures
        `class`.cn.signature = null
        return `class`.cn
    }

    operator fun invoke(): ClassNode = build()
}