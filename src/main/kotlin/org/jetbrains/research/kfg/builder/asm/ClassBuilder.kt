package org.jetbrains.research.kfg.builder.asm

import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.visitor.ClassVisitor
import org.objectweb.asm.tree.ClassNode

class ClassBuilder(`class`: Class): ClassVisitor(`class`) {

    override fun visitMethod(method: Method) {
        AsmBuilder(method).build()
    }

    fun build(): ClassNode {
        visit()
        return `class`.cn
    }
}