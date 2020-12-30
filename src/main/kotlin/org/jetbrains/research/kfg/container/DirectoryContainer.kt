package org.jetbrains.research.kfg.container

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.util.*
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
            val commonSubstring = longestCommonPrefix(klasses).dropLastWhile { it != '/' }
            return Package.parse("$commonSubstring*")
        }

    constructor(path: Path, pkg: Package?) : this(path.toFile(), pkg)
    constructor(path: String, pkg: Package?) : this(Paths.get(path), pkg)
    constructor(path: String, pkg: String) : this(Paths.get(path), Package.parse(pkg))

    private val File.fullClassName: String get() = this.relativeTo(file).path

    override fun parse(flags: Flags): Map<String, ClassNode> {
        val classes = mutableMapOf<String, ClassNode>()
        for (entry in file.allEntries) {
            if (entry.isClass && pkg.isParent(entry.fullClassName)) {
                val classNode = readClassNode(entry.inputStream(), flags)

                // need to recompute frames because sometimes original Jar classes don't contain frame info
                classes[classNode.name] = when {
                    classNode.hasFrameInfo -> classNode
                    else -> classNode.recomputeFrames(classLoader)
                }
            }

        }
        return classes
    }

    override fun unpack(cm: ClassManager, target: Path, unpackAllClasses: Boolean) {
        val loader = file.classLoader

        val absolutePath = target.toAbsolutePath()
        for (entry in file.allEntries) {
            if (entry.isClass) {
                val `class` = cm[entry.fullClassName]
                when {
                    pkg.isParent(entry.name) && `class` is ConcreteClass -> {
                        val localPath = "${`class`.fullname}.class"
                        val path = "$absolutePath/$localPath"
                        `class`.write(cm, loader, path, Flags.writeComputeFrames)
                    }
                    unpackAllClasses -> {
                        val path = "$absolutePath/${entry.fullClassName}"
                        val classNode = readClassNode(entry.inputStream())
                        classNode.write(loader, path, Flags.writeComputeNone)
                    }
                }
            }
        }
    }

    override fun update(cm: ClassManager, target: Path): Container {
        val absolutePath = target.toAbsolutePath()
        unpack(cm, target)

        for (entry in file.allEntries) {
            if (entry.isClass && pkg.isParent(entry.fullClassName)) {
                val `class` = cm[entry.fullClassName]

                if (`class` is ConcreteClass) {
                    val localName = "${`class`.fullname}.class"

                    File(absolutePath.toString(), localName).write(entry.inputStream())
                }
            }
        }
        return DirectoryContainer(target.toFile(), pkg)
    }

}