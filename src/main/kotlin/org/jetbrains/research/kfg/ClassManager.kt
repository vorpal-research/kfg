package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.builder.cfg.CfgBuilder
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.ir.OuterClass
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.util.JarUtils
import org.jetbrains.research.kfg.util.simpleHash
import org.objectweb.asm.tree.*
import java.util.jar.JarFile

class Package(name: String) {
    val name: String
    val isConcrete: Boolean = name.lastOrNull() != '*'

    init {
        this.name = when {
            isConcrete -> name
            else -> name.dropLast(1)
        }
    }

    fun isParent(other: Package) = when {
        isConcrete -> this.name == other.name
        else -> other.name.startsWith(this.name)
    }
    fun isChild(other: Package) = other.isParent(this)

    fun isParent(name: String) = isParent(Package(name))
    fun isChild(name: String) = isChild(Package(name))

    override fun toString() = "$name${if (isConcrete) "" else "*"}"
    override fun hashCode() = simpleHash(name, isConcrete)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Package
        return this.name == other.name && this.isConcrete == other.isConcrete
    }
}

object ClassManager {
    val classNodes = hashMapOf<String, ClassNode>()
    val classes = hashMapOf<String, Class>()

    fun parseJar(jar: JarFile, `package`: Package = Package("*"), flags: Flags = Flags.readSkipFrames) {
        val jarClasses = JarUtils.parseJarClasses(jar, `package`, flags)
        classNodes.putAll(jarClasses)
        jarClasses.forEach { (name, cn) ->
            classes.getOrPut(name, { ConcreteClass(cn) }).init()
        }
        classes.values.forEach {
            it.methods.forEach { _, method ->
                if (!method.isAbstract) CfgBuilder(method).build()
            }
        }
    }

    fun get(cn: ClassNode) = classes.getOrPut(cn.name) { ConcreteClass(cn) }
    fun getConcreteClasses() = classes.values.mapNotNull { it as? ConcreteClass }

    fun getByName(name: String): Class {
        var cn = classNodes[name]
        return if (cn != null) get(cn) else {
            cn = ClassNode()
            cn.name = name
            OuterClass(cn)
        }
    }

    fun getByPackage(`package`: Package): List<Class> = getConcreteClasses().filter { `package`.isParent(it.`package`) }
}