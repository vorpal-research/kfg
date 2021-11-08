package org.jetbrains.research.kfg.builder.cfg

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.visitor.ClassVisitor
import org.jetbrains.research.kfg.visitor.Pipeline

class InnerClassNormalizer(override val cm: ClassManager) : ClassVisitor {
    private val _pipeline = object : Pipeline(cm) {
        override fun run() {
            // Do nothing
        }
    }
    override val pipeline: Pipeline get() = _pipeline
    override fun cleanup() {}

    override fun visit(klass: Class) {
        if (klass.cn.innerClasses == null) return
        val iterator = klass.cn.innerClasses.iterator()
        while (iterator.hasNext()) {
            val innerClassNode = iterator.next()
            if (innerClassNode.outerName != klass.cn.name && innerClassNode.outerName != null) {
                iterator.remove()
                val outer = cm[innerClassNode.outerName]
                val inner = cm[innerClassNode.name]
                if (outer.cn.innerClasses.all { it.name != innerClassNode.name }) outer.cn.innerClasses.add(innerClassNode)
                if (inner.cn.outerClass != innerClassNode.outerName) inner.cn.outerClass = innerClassNode.outerName
            }
        }
    }
}