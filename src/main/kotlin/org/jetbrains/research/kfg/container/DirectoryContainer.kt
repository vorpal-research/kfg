package org.jetbrains.research.kfg.container

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.UnsupportedCfgException
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.util.*
import org.jetbrains.research.kthelper.`try`
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class DirectoryContainer(private val file: File, pkg: Package? = null) : Container {
    override val pkg: Package = pkg ?: commonPackage

    override val name: String
        get() = file.absolutePath

    override val classLoader: ClassLoader
        get() = file.classLoader

    override val commonPackage: Package
        get() {
            val klasses = file.allEntries.filter { it.isClass }.map { it.fullClassName }
            val commonSubstring = longestCommonPrefix(klasses).dropLastWhile { it != Package.SEPARATOR }
            return Package.parse("$commonSubstring*")
        }

    constructor(path: Path, pkg: Package?) : this(path.toFile(), pkg)
    constructor(path: String, pkg: Package?) : this(Paths.get(path), pkg)
    constructor(path: String, pkg: String) : this(Paths.get(path), Package.parse(pkg))

    private val File.fullClassName: String get() = this.relativeTo(file).path

    private fun <T> failSafeAction(failOnError: Boolean, action: () -> T): T? = `try`<T?> {
        action()
    }.getOrElse {
        if (failOnError) throw UnsupportedCfgException()
        else null
    }

    override fun parse(flags: Flags, failOnError: Boolean, loader: ClassLoader): Map<String, ClassNode> {
        val classes = mutableMapOf<String, ClassNode>()
        for (entry in file.allEntries) {
            if (entry.isClass && pkg.isParent(entry.fullClassName)) {
                val classNode = readClassNode(entry.inputStream(), flags)

                // need to recompute frames because sometimes original Jar classes don't contain frame info
                val newClassNode = when {
                    classNode.hasFrameInfo -> classNode
                    else -> failSafeAction(failOnError) { classNode.recomputeFrames(loader) }
                } ?: continue
                classes[classNode.name] = newClassNode
            }

        }
        return classes
    }

    override fun unpack(cm: ClassManager, target: Path, unpackAllClasses: Boolean, failOnError: Boolean, loader: ClassLoader) {
        val absolutePath = target.toAbsolutePath()
        for (entry in file.allEntries) {
            if (entry.isClass) {
                val `class` = cm[entry.fullClassName]
                when {
                    pkg.isParent(entry.name) && `class` is ConcreteClass -> {
                        val localPath = "${`class`.fullName}.class"
                        val path = "$absolutePath/$localPath"
                        failSafeAction(failOnError) { `class`.write(cm, loader, path, Flags.writeComputeFrames) }
                    }
                    unpackAllClasses -> {
                        val path = "$absolutePath${File.separator}${entry.fullClassName}"
                        val classNode = readClassNode(entry.inputStream())
                        failSafeAction(failOnError) { classNode.write(loader, path, Flags.writeComputeNone) }
                    }
                }
            }
        }
    }

    override fun update(cm: ClassManager, target: Path, loader: ClassLoader): Container {
        val absolutePath = target.toAbsolutePath()
        unpack(cm, target)

        for (entry in file.allEntries) {
            if (entry.isClass && pkg.isParent(entry.fullClassName)) {
                val `class` = cm[entry.fullClassName]

                if (`class` is ConcreteClass) {
                    val localName = "${`class`.fullName}.class"

                    File(absolutePath.toString(), localName).write(entry.inputStream())
                }
            }
        }
        return DirectoryContainer(target.toFile(), pkg)
    }

}