package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.builder.cfg.CfgBuilder
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.ir.OuterClass
import org.jetbrains.research.kfg.ir.value.ValueFactory
import org.jetbrains.research.kfg.ir.value.instruction.InstructionFactory
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.util.parseJarClasses
import org.jetbrains.research.kfg.util.simpleHash
import org.objectweb.asm.tree.ClassNode
import java.util.jar.JarFile

class Package(name: String) {
    val name: String
    val isConcrete: Boolean = name.lastOrNull() != '*'

    companion object {
        fun parse(string: String) = Package(string.replace('.', '/'))
    }

    init {
        this.name = when {
            isConcrete -> name
            else -> name.removeSuffix("*").removeSuffix("/")
        }
    }

    fun isParent(other: Package) = when {
        isConcrete -> this.name == other.name
        else -> other.name.startsWith(this.name)
    }

    fun isChild(other: Package) = other.isParent(this)
    fun isParent(name: String) = isParent(Package(name))
    fun isChild(name: String) = isChild(Package(name))

    override fun toString() = "$name${if (isConcrete) "" else "/*"}"
    override fun hashCode() = simpleHash(name, isConcrete)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Package
        return this.name == other.name && this.isConcrete == other.isConcrete
    }
}

class ClassManager(jar: JarFile, val `package`: Package = Package("*"), flags: Flags = Flags.readSkipFrames) {
    val value = ValueFactory(this)
    val instruction = InstructionFactory(this)
    val type = TypeFactory(this)
    val concreteClasses: List<ConcreteClass>

    private val classNodes = hashMapOf<String, ClassNode>()
    private val classes = hashMapOf<String, Class>()

    init {
        val jarClasses = parseJarClasses(jar, `package`, flags)
        classNodes.putAll(jarClasses)
        jarClasses.forEach { (name, cn) ->
            classes.getOrPut(name) { ConcreteClass(this, cn) }.init()
        }
        classes.values.forEach {
            it.methods.forEach { (_, method) ->
                if (!method.isAbstract) CfgBuilder(this, method).build()
            }
        }
        concreteClasses = classes.values.mapNotNull { it as? ConcreteClass }
    }

    fun get(cn: ClassNode) = classes.getOrPut(cn.name) { ConcreteClass(this, cn) }

    fun getByName(name: String): Class {
        var cn = classNodes[name]
        return if (cn != null) get(cn) else {
            cn = ClassNode()
            cn.name = name
            OuterClass(this, cn)
        }
    }

    fun getByPackage(`package`: Package): List<Class> = concreteClasses.filter { `package`.isParent(it.`package`) }
}