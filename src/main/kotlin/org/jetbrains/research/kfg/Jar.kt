package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.ir.ConcreteClass
import org.jetbrains.research.kfg.util.*
import org.objectweb.asm.tree.ClassNode
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.Manifest

data class Jar(private val file: JarFile, val `package`: Package) {
    private val manifest = Manifest()

    constructor(path: Path, `package`: Package) : this(JarFile(path.toFile()), `package`)
    constructor(path: String, `package`: Package) : this(Paths.get(path), `package`)
    constructor(path: String, `package`: String) : this(Paths.get(path), Package.parse(`package`))

    val name get() = file.name
    val classLoader get() = file.classLoader

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

    fun parse(flags: Flags): Map<String, ClassNode> {
        val classes = mutableMapOf<String, ClassNode>()
        val enumeration = file.entries()
        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement() as JarEntry

            if (entry.isClass && `package`.isParent(entry.className)) {
                val classNode = readClassNode(file.getInputStream(entry), flags)
                classes[classNode.name] = classNode
            }

        }
        return classes
    }

    fun unpack(cm: ClassManager, target: Path, unpackAllClasses: Boolean = false) {
        val loader = file.classLoader

        val absolutePath = target.toAbsolutePath()
        val enumeration = file.entries()

        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement() as JarEntry
            if (entry.isManifest) continue

            if (entry.isClass) {
                val `class` = cm.get(entry.name.removeSuffix(".class"))
                when {
                    `package`.isParent(entry.name) && `class` is ConcreteClass -> {
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

    fun update(cm: ClassManager) = update(cm, Files.createTempDirectory("kfg"))

    fun update(cm: ClassManager, target: Path): Jar {
        val absolutePath = target.toAbsolutePath()
        val jarName = file.name.substringAfterLast('/').removeSuffix(".jar")
        val builder = JarBuilder("$absolutePath/$jarName.jar", manifest)
        val enumeration = file.entries()

        unpack(cm, target)

        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement() as JarEntry
            if (entry.isManifest) continue

            if (entry.isClass && `package`.isParent(entry.name)) {
                val `class` = cm.get(entry.name.removeSuffix(".class"))

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
        return Jar(builder.name, `package`)
    }

}