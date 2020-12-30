package org.jetbrains.research.kfg.container

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.util.*
import org.objectweb.asm.tree.ClassNode
import java.io.FileInputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.Manifest

class JarContainer(private val file: JarFile, pkg: Package? = null) : Container {
    private val manifest = Manifest()

    constructor(path: Path, `package`: Package?) : this(JarFile(path.toFile()), `package`)
    constructor(path: String, `package`: Package?) : this(Paths.get(path), `package`)
    constructor(path: String, `package`: String) : this(Paths.get(path), Package.parse(`package`))

    override val pkg: Package = pkg ?: commonPackage
    override val name: String get() = file.name
    override val classLoader get() = file.classLoader

    override val commonPackage: Package
        get() {
            val klasses = mutableListOf<String>()
            val enumeration = file.entries()
            while (enumeration.hasMoreElements()) {
                val entry = enumeration.nextElement() as JarEntry

                if (entry.isClass) {
                    klasses += entry.name
                }

            }
            val commonSubstring = longestCommonPrefix(klasses).dropLastWhile { it != '/' }
            return Package.parse("$commonSubstring*")
        }

    init {
        if (file.manifest != null) {
            for ((key, value) in file.manifest.mainAttributes) {
                manifest.mainAttributes[key] = value
            }
            for ((key, value) in file.manifest.entries) {
                manifest.entries[key] = value
            }
        }
    }

    override fun parse(flags: Flags): Map<String, ClassNode> {
        val classes = mutableMapOf<String, ClassNode>()
        val enumeration = file.entries()
        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement() as JarEntry

            if (entry.isClass && pkg.isParent(entry.className)) {
                val classNode = readClassNode(file.getInputStream(entry), flags)

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
        val enumeration = file.entries()

        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement() as JarEntry
            if (entry.isManifest) continue

            if (entry.isClass) {
                val `class` = cm[entry.name.removeSuffix(".class")]
                when {
                    pkg.isParent(entry.name) && `class` is ConcreteClass -> {
                        val localPath = "${`class`.fullname}.class"
                        val path = "$absolutePath/$localPath"
                        `class`.write(cm, loader, path, Flags.writeComputeFrames)
                    }
                    unpackAllClasses -> {
                        val path = "$absolutePath/${entry.name}"
                        val classNode = readClassNode(file.getInputStream(entry))
                        classNode.write(loader, path, Flags.writeComputeNone)
                    }
                }
            }
        }
    }

    override fun update(cm: ClassManager, target: Path): JarContainer {
        val absolutePath = target.toAbsolutePath()
        val jarName = file.name.substringAfterLast('/').removeSuffix(".jar")
        val builder = JarBuilder("$absolutePath/$jarName.jar", manifest)
        val enumeration = file.entries()

        unpack(cm, target)

        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement() as JarEntry
            if (entry.isManifest) continue

            if (entry.isClass && pkg.isParent(entry.name)) {
                val `class` = cm[entry.name.removeSuffix(".class")]

                if (`class` is ConcreteClass) {
                    val localPath = "${`class`.fullname}.class"
                    val path = "$absolutePath/$localPath"

                    val newEntry = JarEntry(localPath.replace("\\", "/"))
                    builder.add(newEntry, FileInputStream(path))
                } else {
                    builder.add(entry, file.getInputStream(entry))
                }
            } else {
                builder.add(entry, file.getInputStream(entry))
            }
        }
        builder.close()
        return JarContainer(builder.name, pkg)
    }

}