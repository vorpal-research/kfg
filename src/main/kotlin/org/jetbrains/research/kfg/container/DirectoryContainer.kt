package org.jetbrains.research.kfg.container

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.util.*
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.nio.file.Path

class DirectoryContainer(private val file: File, override val pkg: Package) : Container {
    override val name: String
        get() = file.absolutePath

    override val classLoader: ClassLoader
        get() = file.classLoader

    constructor(path: Path, pkg: Package) : this(path.toFile(), pkg)

    override fun parse(flags: Flags): Map<String, ClassNode> {
        val classes = mutableMapOf<String, ClassNode>()
        for (entry in file.allEntries) {
            if (entry.isClass && pkg.isParent(entry.className)) {
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
                val `class` = cm[entry.className]
                when {
                    pkg.isParent(entry.name) && `class` is ConcreteClass -> {
                        val localPath = "${`class`.fullname}.class"
                        val path = "$absolutePath/$localPath"
                        `class`.write(cm, loader, path, Flags.writeComputeFrames)
                    }
                    unpackAllClasses -> {
                        val path = "$absolutePath/${entry.name}"
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
            if (entry.isClass && pkg.isParent(entry.name)) {
                val `class` = cm[entry.className]

                if (`class` is ConcreteClass) {
                    val localName = "${`class`.fullname}.class"

                    File(absolutePath.toString(), localName).write(entry.inputStream())
                }
            }
        }
        return DirectoryContainer(target.toFile(), pkg)
    }

}