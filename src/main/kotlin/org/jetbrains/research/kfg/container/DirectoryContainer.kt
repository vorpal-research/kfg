package org.jetbrains.research.kfg.container

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.UnsupportedCfgException
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.util.*
import org.jetbrains.research.kthelper.`try`
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

class DirectoryContainer(private val file: File, pkg: Package? = null) : Container {
    override val pkg: Package = pkg ?: commonPackage
    override val path: Path = file.toPath()

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

    private val File.fullClassName: String get() = this.relativeTo(file).path.removeSuffix(".class")
    private val File.pkg: Package get() = Package(fullClassName.dropLastWhile { it != Package.SEPARATOR })

    private fun <T> failSafeAction(failOnError: Boolean, action: () -> T): T? = `try`<T?> {
        action()
    }.getOrElse {
        if (failOnError) throw UnsupportedCfgException()
        else null
    }

    override fun parse(flags: Flags, failOnError: Boolean, loader: ClassLoader): Map<String, ClassNode> {
        val classes = mutableMapOf<String, ClassNode>()
        for (entry in file.allEntries) {
            if (entry.isClass && pkg.isParent(entry.pkg)) {
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
        val allClasses = cm.getContainerClasses(this)
        val visitedClasses = mutableSetOf<Class>()

        for (entry in file.allEntries) {
            if (entry.isClass) {
                val `class` = cm[entry.fullClassName]
                visitedClasses += `class`
                when {
                    pkg.isParent(entry.pkg) && `class` is ConcreteClass -> {
                        val path = absolutePath.resolve(Paths.get(`class`.pkg.fileSystemPath, "${`class`.name}.class"))
                        failSafeAction(failOnError) { `class`.write(cm, loader, path, Flags.writeComputeFrames) }
                    }
                    unpackAllClasses -> {
                        val path = absolutePath.resolve(entry.fullClassName)
                        val classNode = readClassNode(entry.inputStream())
                        failSafeAction(failOnError) { classNode.write(loader, path, Flags.writeComputeNone) }
                    }
                }
            }
        }

        for (newKlass in allClasses.filter { it !in visitedClasses }) {
            when {
                pkg.isParent(newKlass.pkg) || unpackAllClasses -> {
                    val path = absolutePath.resolve(Paths.get(newKlass.pkg.fileSystemPath, "${newKlass.name}.class"))
                    failSafeAction(failOnError) { newKlass.write(cm, loader, path, Flags.writeComputeFrames) }
                }
            }
        }
    }

    override fun update(cm: ClassManager, target: Path, loader: ClassLoader): Container {
        val absolutePath = target.toAbsolutePath()
        unpack(cm, target)

        for (entry in file.allEntries) {
            if (entry.isClass && pkg.isParent(entry.pkg)) {
                val `class` = cm[entry.fullClassName]

                if (`class` is ConcreteClass) {
                    val localName = "${`class`.pkg.fileSystemPath}${File.separator}${`class`.name}.class"

                    File(absolutePath.toString(), localName).write(entry.inputStream())
                }
            }
        }
        return DirectoryContainer(target.toFile(), pkg)
    }

}