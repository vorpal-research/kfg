package org.jetbrains.research.kfg.visitor

import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method

open class ClassVisitor(val `class`: Class) : NodeVisitor(`class`) {
    override fun visit() {
        super.visit()
        `class`.run {
            fields.values.forEach { visitField(it) }
            methods.values.forEach { visitMethod(it) }
        }
    }

    open fun visitInterface(`interface`: Class) {}
    open fun visitField(field: Field) {}
    open fun visitMethod(method: Method) {}
}