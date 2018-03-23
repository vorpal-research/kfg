package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.builder.cfg.CfgBuilder
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.ir.OuterClass
import org.jetbrains.research.kfg.util.parseJarClasses
import org.objectweb.asm.tree.*
import java.util.jar.JarFile

object ClassManager {
    val classNodes = mutableMapOf<String, ClassNode>()
    val classes = mutableMapOf<String, Class>()

    fun parseJar(jar: JarFile) {
        val jarClasses = parseJarClasses(jar)
        classNodes.putAll(jarClasses)
        jarClasses.forEach { (name, cn) ->
            classes.getOrPut(name, { ConcreteClass(cn) }).init()
        }
        classes.values.forEach {
            it.methods.forEach { _, method ->
                if (!method.isAbstract()) CfgBuilder(method).build()
            }
        }
    }

    fun get(cn: ClassNode) = classes.getOrPut(cn.name, { ConcreteClass(cn) })

    fun getByName(name: String): Class {
        var cn = classNodes[name]
        return if (cn != null) get(cn) else {
            cn = ClassNode()
            cn.name = name
            OuterClass(cn)
        }
    }
}