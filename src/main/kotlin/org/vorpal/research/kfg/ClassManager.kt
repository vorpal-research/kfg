@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package org.vorpal.research.kfg

import org.objectweb.asm.tree.ClassNode
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
import java.io.File

data class Package(val components: List<String>, val isConcrete: Boolean) {
    companion object {
        const val SEPARATOR = '/'
        const val SEPARATOR_STR = SEPARATOR.toString()
        const val EXPANSION = '*'
        const val EXPANSION_STR = EXPANSION.toString()
        const val CANONICAL_SEPARATOR = '.'
        const val CANONICAL_SEPARATOR_STR = CANONICAL_SEPARATOR.toString()
        val defaultPackage = Package(EXPANSION_STR)
        val emptyPackage = Package("")
        fun parse(string: String) = Package(
            string.replace(
                CANONICAL_SEPARATOR,
                SEPARATOR
            )
        )
    }

    constructor(name: String) : this(
        name.removeSuffix(EXPANSION_STR)
            .removeSuffix(SEPARATOR_STR)
            .split(SEPARATOR)
            .filter { it.isNotBlank() },
        name.lastOrNull() != EXPANSION
    )

    val concretePackage get() = if (isConcrete) this else Package(concreteName)
    val concreteName get() = components.joinToString(SEPARATOR_STR)
    val canonicalName get() = components.joinToString(CANONICAL_SEPARATOR_STR)
    val fileSystemPath get() = components.joinToString(File.separator)

    val concretized: Package
        get() = when {
            isConcrete -> this
            else -> copy(isConcrete = true)
        }
    val expanded: Package
        get() = when {
            isConcrete -> copy(isConcrete = false)
            else -> this
        }

    fun isParent(other: Package): Boolean = when {
        isConcrete -> this.components == other.components
        this.components.size > other.components.size -> false
        else -> this.components.indices.fold(true) { acc, i ->
            acc && (this[i] == other[i])
        }
    }

    operator fun get(i: Int) = components[i]

    fun isChild(other: Package) = other.isParent(this)
    fun isParent(name: String) = isParent(Package(name))
    fun isChild(name: String) = isChild(Package(name))

    override fun toString() = buildString {
        append(components.joinToString(SEPARATOR_STR))
        if (!isConcrete) {
            if (components.isNotEmpty()) append(SEPARATOR)
            append(EXPANSION)
        }
    }

    override fun hashCode(): Int {
        var result = components.hashCode()
        result = 31 * result + isConcrete.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != this.javaClass) return false
        other as Package
        return this.components == other.components && this.isConcrete == other.isConcrete
    }
}

class ClassManager(val config: KfgConfig = KfgConfigBuilder().build()) {
    val value = ValueFactory(this)
    val instruction = InstructionFactory(this)
    val type = TypeFactory(this)
    internal val loopManager: LoopManager by lazy {
        when {
            config.useCachingLoopManager -> CachingLoopManager(this)
            else -> DefaultLoopManager()
        }
    }

    val flags: Flags get() = config.flags
    val failOnError: Boolean get() = config.failOnError
    val verifyIR: Boolean get() = config.verifyIR

    private val classes = hashMapOf<String, Class>()
    private val outerClasses = hashMapOf<String, Class>()
    private val container2class = hashMapOf<Container, MutableSet<Class>>()

    val concreteClasses: Set<ConcreteClass>
        get() = classes.values.filterIsInstanceTo(mutableSetOf())

    fun initialize(loader: ClassLoader, vararg containers: Container) {
        initialize(loader, containers.toList())
    }

    fun initialize(vararg containers: Container) {
        initialize(containers.toList())
    }

    fun initialize(loader: ClassLoader, containers: List<Container>) {
        val container2ClassNode = containers.associateWith { it.parse(flags, config.failOnError, loader) }
        initialize(container2ClassNode)
    }

    fun initialize(containers: List<Container>) {
        val container2ClassNode = containers.associateWith { it.parse(flags) }
        initialize(container2ClassNode)
    }

    private fun initialize(container2ClassNode: Map<Container, Map<String, ClassNode>>) {
        for ((container, classNodes) in container2ClassNode) {
            classNodes.forEach { (name, cn) ->
                val klass = ConcreteClass(this, cn)
                classes[name] = klass
                container2class.getOrPut(container, ::mutableSetOf).add(klass)
            }
        }
        for (klass in classes.values) {
            InnerClassNormalizer(this).visit(klass)
        }
        classes.values.forEach { it.init() }
    }

    operator fun get(name: String): Class = classes[name] ?: outerClasses.getOrPut(name) {
        val pkg = Package.parse(name.substringBeforeLast(Package.SEPARATOR))
        val klassName = name.substringAfterLast(Package.SEPARATOR)
        OuterClass(this, pkg, klassName)
    }

    fun getByPackage(`package`: Package): List<Class> = concreteClasses.filter { `package`.isParent(it.pkg) }

    fun getSubtypesOf(klass: Class): Set<Class> =
        concreteClasses.filterTo(mutableSetOf()) { it.isInheritorOf(klass) && it != klass }

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
        pkg: Package,
        name: String,
        modifiers: Modifiers = Modifiers(0)
    ): Class {
        val klass = ConcreteClass(this, pkg, name, modifiers)
        classes[klass.fullName] = klass
        container2class.getOrPut(container, ::mutableSetOf).add(klass)
        return klass
    }

    fun getContainerClasses(container: Container): Set<Class> = container2class.getOrDefault(container, emptySet())
}

val ClassManager.classClass
    get() = this[SystemTypeNames.classClass]

val ClassManager.stringClass
    get() = this[SystemTypeNames.stringClass]

val ClassManager.objectClass
    get() = this[SystemTypeNames.objectClass]

val ClassManager.boolWrapper
    get() = this[SystemTypeNames.booleanClass]

val ClassManager.byteWrapper
    get() = this[SystemTypeNames.byteClass]

val ClassManager.charWrapper
    get() = this[SystemTypeNames.charClass]

val ClassManager.shortWrapper
    get() = this[SystemTypeNames.shortClass]

val ClassManager.intWrapper
    get() = this[SystemTypeNames.integerClass]

val ClassManager.longWrapper
    get() = this[SystemTypeNames.longClass]

val ClassManager.floatWrapper
    get() = this[SystemTypeNames.floatClass]

val ClassManager.doubleWrapper
    get() = this[SystemTypeNames.doubleClass]

val ClassManager.collectionClass
    get() = this[SystemTypeNames.collectionClass]

val ClassManager.listClass
    get() = this[SystemTypeNames.listClass]

val ClassManager.arrayListClass
    get() = this[SystemTypeNames.arrayListClass]

val ClassManager.linkedListClass
    get() = this[SystemTypeNames.linkedListClass]

val ClassManager.queueClass
    get() = this[SystemTypeNames.queueClass]

val ClassManager.dequeClass
    get() = this[SystemTypeNames.dequeClass]

val ClassManager.arrayDequeClass
    get() = this[SystemTypeNames.arrayDequeClass]

val ClassManager.setClass
    get() = this[SystemTypeNames.setClass]

val ClassManager.sortedSetClass
    get() = this[SystemTypeNames.sortedSetClass]

val ClassManager.navigableSetClass
    get() = this[SystemTypeNames.navigableSetClass]

val ClassManager.hashSetClass
    get() = this[SystemTypeNames.hashSetClass]

val ClassManager.treeSetClass
    get() = this[SystemTypeNames.treeSetClass]

val ClassManager.mapClass
    get() = this[SystemTypeNames.mapClass]

val ClassManager.sortedMapClass
    get() = this[SystemTypeNames.sortedMapClass]

val ClassManager.navigableMapClass
    get() = this[SystemTypeNames.navigableMapClass]

val ClassManager.hashMapClass
    get() = this[SystemTypeNames.hashMapClass]

val ClassManager.treeMapClass
    get() = this[SystemTypeNames.treeMapClass]
val ClassManager.classLoaderClass
    get() = this[SystemTypeNames.classLoader]
val ClassManager.stringBuilderClass
    get() = this[SystemTypeNames.stringBuilder]
val ClassManager.stringBufferClass
    get() = this[SystemTypeNames.stringBuffer]
val ClassManager.linkedHashSetClass
    get() = this[SystemTypeNames.linkedHashSet]
val ClassManager.linkedHashMapClass
    get() = this[SystemTypeNames.linkedHashMap]
