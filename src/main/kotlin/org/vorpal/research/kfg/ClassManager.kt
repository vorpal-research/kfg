package org.vorpal.research.kfg

import org.objectweb.asm.tree.ClassNode
import org.vorpal.research.kfg.builder.cfg.CfgBuilder
import org.vorpal.research.kfg.builder.cfg.InnerClassNormalizer
import org.vorpal.research.kfg.container.Container
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.ConcreteClass
import org.vorpal.research.kfg.ir.Modifiers
import org.vorpal.research.kfg.ir.OuterClass
import org.vorpal.research.kfg.ir.value.ValueFactory
import org.vorpal.research.kfg.ir.value.instruction.InstructionFactory
import org.vorpal.research.kfg.type.SystemTypeNames
import org.vorpal.research.kfg.type.TypeFactory
import org.vorpal.research.kfg.util.Flags
import org.vorpal.research.kthelper.KtException
import org.vorpal.research.kthelper.defaultHashCode
import java.io.File

data class Package(val components: List<String>, val isConcrete: Boolean) {
    companion object {
        const val SEPARATOR = '/'
        const val SEPARATOR_STR = org.vorpal.research.kfg.Package.Companion.SEPARATOR.toString()
        const val EXPANSION = '*'
        const val EXPANSION_STR = org.vorpal.research.kfg.Package.Companion.EXPANSION.toString()
        const val CANONICAL_SEPARATOR = '.'
        const val CANONICAL_SEPARATOR_STR = org.vorpal.research.kfg.Package.Companion.CANONICAL_SEPARATOR.toString()
        val defaultPackage = org.vorpal.research.kfg.Package(org.vorpal.research.kfg.Package.Companion.EXPANSION_STR)
        val emptyPackage = org.vorpal.research.kfg.Package("")
        fun parse(string: String) = org.vorpal.research.kfg.Package(
            string.replace(
                org.vorpal.research.kfg.Package.Companion.CANONICAL_SEPARATOR,
                org.vorpal.research.kfg.Package.Companion.SEPARATOR
            )
        )
    }

    constructor(name: String) : this(
        name.removeSuffix(org.vorpal.research.kfg.Package.Companion.EXPANSION_STR)
            .removeSuffix(org.vorpal.research.kfg.Package.Companion.EXPANSION_STR)
            .split(org.vorpal.research.kfg.Package.Companion.SEPARATOR)
            .filter { it.isNotBlank() },
        name.lastOrNull() != org.vorpal.research.kfg.Package.Companion.EXPANSION
    )

    val concretePackage get() = if (isConcrete) this else org.vorpal.research.kfg.Package(concreteName)
    val concreteName get() = components.joinToString(org.vorpal.research.kfg.Package.Companion.SEPARATOR_STR)
    val canonicalName get() = components.joinToString(org.vorpal.research.kfg.Package.Companion.CANONICAL_SEPARATOR_STR)
    val fileSystemPath get() = components.joinToString(File.separator)

    val concretized: org.vorpal.research.kfg.Package
        get() = when {
            isConcrete -> this
            else -> copy(isConcrete = true)
        }
    val expanded: org.vorpal.research.kfg.Package
        get() = when {
            isConcrete -> copy(isConcrete = false)
            else -> this
        }

    fun isParent(other: org.vorpal.research.kfg.Package) = when {
        isConcrete -> this.components == other.components
        this.components.size > other.components.size -> false
        else -> this.components.indices.fold(true) { acc, i ->
            acc && (this[i] == other[i])
        }
    }

    operator fun get(i: Int) = components[i]

    fun isChild(other: org.vorpal.research.kfg.Package) = other.isParent(this)
    fun isParent(name: String) = isParent(org.vorpal.research.kfg.Package(name))
    fun isChild(name: String) = isChild(org.vorpal.research.kfg.Package(name))

    override fun toString() = buildString {
        append(components.joinToString(org.vorpal.research.kfg.Package.Companion.SEPARATOR_STR))
        if (!isConcrete) {
            if (components.isNotEmpty()) append(org.vorpal.research.kfg.Package.Companion.SEPARATOR)
            append(org.vorpal.research.kfg.Package.Companion.EXPANSION)
        }
    }

    override fun hashCode() = defaultHashCode(components, isConcrete)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as org.vorpal.research.kfg.Package
        return this.components == other.components && this.isConcrete == other.isConcrete
    }
}

class ClassManager(val config: org.vorpal.research.kfg.KfgConfig = org.vorpal.research.kfg.KfgConfigBuilder().build()) {
    val value = ValueFactory(this)
    val instruction = InstructionFactory(this)
    val type = TypeFactory(this)
    internal val loopManager: org.vorpal.research.kfg.LoopManager by lazy {
        when {
            config.useCachingLoopManager -> org.vorpal.research.kfg.CachingLoopManager(this)
            else -> org.vorpal.research.kfg.DefaultLoopManager()
        }
    }

    val flags: Flags get() = config.flags
    val failOnError: Boolean get() = config.failOnError
    val verifyIR: Boolean get() = config.verifyIR

    private val classes = hashMapOf<String, Class>()
    private val outerClasses = hashMapOf<String, Class>()
    private val class2container = hashMapOf<Class, Container>()
    private val container2class = hashMapOf<Container, MutableSet<Class>>()

    val concreteClasses get() = classes.values.filterIsInstance<ConcreteClass>().toSet()

    val classClass
        get() = this[SystemTypeNames.classClass]

    val stringClass
        get() = this[SystemTypeNames.stringClass]

    val objectClass
        get() = this[SystemTypeNames.objectClass]

    val boolWrapper
        get() = this[SystemTypeNames.booleanClass]

    val byteWrapper
        get() = this[SystemTypeNames.byteClass]

    val charWrapper
        get() = this[SystemTypeNames.charClass]

    val shortWrapper
        get() = this[SystemTypeNames.shortClass]

    val intWrapper
        get() = this[SystemTypeNames.integerClass]

    val longWrapper
        get() = this[SystemTypeNames.longClass]

    val floatWrapper
        get() = this[SystemTypeNames.floatClass]

    val doubleWrapper
        get() = this[SystemTypeNames.doubleClass]

    val collectionClass
        get() = this[SystemTypeNames.collectionClass]

    val listClass
        get() = this[SystemTypeNames.listClass]

    val arrayListClass
        get() = this[SystemTypeNames.arrayListClass]

    val linkedListClass
        get() = this[SystemTypeNames.linkedListClass]

    val queueClass
        get() = this[SystemTypeNames.queueClass]
    val dequeClass
        get() = this[SystemTypeNames.dequeClass]
    val arrayDequeClass
        get() = this[SystemTypeNames.arrayDequeClass]

    val setClass
        get() = this[SystemTypeNames.setClass]

    val sortedSetClass
        get() = this[SystemTypeNames.sortedSetClass]
    val navigableSetClass
        get() = this[SystemTypeNames.navigableSetClass]

    val hashSetClass
        get() = this[SystemTypeNames.hashSetClass]

    val treeSetClass
        get() = this[SystemTypeNames.treeSetClass]

    val mapClass
        get() = this[SystemTypeNames.mapClass]

    val sortedMapClass
        get() = this[SystemTypeNames.sortedMapClass]

    val navigableMapClass
        get() = this[SystemTypeNames.navigableMapClass]

    val hashMapClass
        get() = this[SystemTypeNames.hashMapClass]

    val treeMapClass
        get() = this[SystemTypeNames.treeMapClass]

    fun initialize(loader: ClassLoader, vararg containers: Container) {
        val container2ClassNode = containers.associateWith { it.parse(flags, config.failOnError, loader) }
        initialize(container2ClassNode)
    }

    fun initialize(vararg containers: Container) {
        val container2ClassNode = containers.associateWith { it.parse(flags) }
        initialize(container2ClassNode)
    }

    private fun initialize(container2ClassNode: Map<Container, Map<String, ClassNode>>) {
        for ((container, classNodes) in container2ClassNode) {
            classNodes.forEach { (name, cn) ->
                val klass = ConcreteClass(this, cn)
                classes[name] = klass
                class2container[klass] = container
                container2class.getOrPut(container, ::mutableSetOf).add(klass)
            }
        }
        for (klass in classes.values) {
            InnerClassNormalizer(this).visit(klass)
        }
        classes.values.forEach { it.init() }

        for (klass in classes.values) {
            for (method in klass.allMethods) {
                try {
                    if (!method.isAbstract) CfgBuilder(this, method).build()
                } catch (e: org.vorpal.research.kfg.KfgException) {
                    if (failOnError) throw e
                    klass.failingMethods += method
                    method.clear()
                } catch (e: KtException) {
                    if (failOnError) throw e
                    klass.failingMethods += method
                    method.clear()
                }
            }
        }
    }

    operator fun get(name: String): Class = classes[name] ?: outerClasses.getOrPut(name) {
        val pkg =
            org.vorpal.research.kfg.Package.Companion.parse(name.substringBeforeLast(org.vorpal.research.kfg.Package.Companion.SEPARATOR))
        val klassName = name.substringAfterLast(org.vorpal.research.kfg.Package.Companion.SEPARATOR)
        OuterClass(this, pkg, klassName)
    }

    fun getByPackage(`package`: org.vorpal.research.kfg.Package): List<Class> = concreteClasses.filter { `package`.isParent(it.pkg) }

    fun getSubtypesOf(klass: Class): Set<Class> =
        concreteClasses.filter { it.isInheritorOf(klass) && it != klass }.toSet()

    fun getAllSubtypesOf(klass: Class): Set<Class> {
        val result = mutableSetOf(klass)
        var current = getSubtypesOf(klass)
        do {
            val newCurrent = mutableSetOf<Class>()
            for (it in current) {
                result += it
                newCurrent.addAll(getSubtypesOf(it))
            }
            current = newCurrent
        } while (current.isNotEmpty())
        return result
    }

    fun createClass(
        container: Container,
        pkg: org.vorpal.research.kfg.Package,
        name: String,
        modifiers: Modifiers = Modifiers(0)
    ): Class {
        val klass = ConcreteClass(this, pkg, name, modifiers)
        classes[klass.fullName] = klass
        class2container[klass] = container
        container2class.getOrPut(container, ::mutableSetOf).add(klass)
        return klass
    }

    fun getContainerClasses(container: Container): Set<Class> = container2class.getOrDefault(container, emptySet())
}