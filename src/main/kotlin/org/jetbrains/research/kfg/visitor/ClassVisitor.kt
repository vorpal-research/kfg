package org.jetbrains.research.kfg.visitor

import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method

interface ClassVisitor : NodeVisitor {
    fun visit(`class`: Class) {
        super.visit(`class`)
        `class`.run {
            fields.forEach { visitField(it) }
            methods.forEach { visitMethod(it) }
        }
    }

    fun visitInterface(`interface`: Class) {}
    fun visitField(field: Field) {}
    fun visitMethod(method: Method) {}
}