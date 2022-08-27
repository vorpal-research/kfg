package org.vorpal.research.kfg.visitor

import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.Field
import org.vorpal.research.kfg.ir.Method

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