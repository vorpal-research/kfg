package org.jetbrains.research.kfg.visitor

import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method

interface ClassVisitor : NodeVisitor {
    fun visit(klass: Class) {
        super.visit(klass)
        klass.run {
            fields.forEach { visitField(it) }
            allMethods.forEach { visitMethod(it) }
        }
    }

    fun visitField(field: Field) {}
    fun visitMethod(method: Method) {}
}