package org.jetbrains.research.kfg.builder.asm

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.visitor.ClassVisitor
import org.objectweb.asm.tree.ClassNode

class ClassBuilder(override val cm: ClassManager, val `class`: Class) : ClassVisitor {
    override fun cleanup() {}

    override fun visitMethod(method: Method) {
        AsmBuilder(cm, method).build()
    }

    fun build(): ClassNode {
        visit(`class`)
        return `class`.cn
    }

    operator fun invoke(): ClassNode = build()
}