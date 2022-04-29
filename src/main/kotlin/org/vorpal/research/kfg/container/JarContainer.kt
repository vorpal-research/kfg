package org.vorpal.research.kfg.container

import org.objectweb.asm.tree.ClassNode
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.UnsupportedCfgException
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.ConcreteClass
import org.vorpal.research.kfg.util.*
import org.vorpal.research.kthelper.`try`
import java.io.File
import java.io.FileInputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.Manifest

class JarContainer(override val path: Path, pkg: Package? = null) : Container {
    private val file = JarFile(path.toFile())
    private val manifest = Manifest()

    constructor(path: String, `package`: Package?) : this(Paths.get(path), `package`)
    constructor(path: String, `package`: String) : this(Paths.get(path), Package.parse(`package`))

    override fun toString(): String = path.toString()

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
            val commonSubstring = longestCommonPrefix(klasses).dropLastWhile { it != Package.SEPARATOR }
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

    private fun <T> failSafeAction(failOnError: Boolean, action: () -> T): T? = `try`<T?> {
        action()
    }.getOrElse {
        if (failOnError) throw UnsupportedCfgException()
        else null
    }

    override fun parse(flags: Flags, failOnError: Boolean, loader: ClassLoader): Map<String, ClassNode> {
        val classes = mutableMapOf<String, ClassNode>()
        val enumeration = file.entries()
        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement() as JarEntry

            if (entry.isClass && pkg.isParent(entry.pkg)) {
                val classNode = readClassNode(file.getInputStream(entry), flags)

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

    override fun unpack(
        cm: ClassManager,
        target: Path,
        unpackAllClasses: Boolean,
        failOnError: Boolean,
        checkClass: Boolean,
        loader: ClassLoader
    ) {
        val absolutePath = target.toAbsolutePath()
        val enumeration = file.entries()
        val allClasses = cm.getContainerClasses(this)
        val visitedClasses = mutableSetOf<Class>()

        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement() as JarEntry
            if (entry.isManifest) continue

            if (entry.isClass) {
                failSafeAction(failOnError) {
                    val `class` = cm[entry.name.removeSuffix(".class")]
                    visitedClasses += `class`
                    when {
                        pkg.isParent(entry.pkg) && `class` is ConcreteClass -> {
                            val path = absolutePath.resolve(Paths.get(`class`.pkg.fileSystemPath, "${`class`.name}.class"))
                            `class`.write(cm, loader, path, Flags.writeComputeFrames, checkClass)
                        }
                        unpackAllClasses -> {
                            val path = absolutePath.resolve(entry.name)
                            val classNode = readClassNode(file.getInputStream(entry))
                            classNode.write(loader, path, Flags.writeComputeNone, checkClass)
                        }
                        else -> Unit
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

    override fun update(cm: ClassManager, target: Path, loader: ClassLoader): JarContainer {
        unpack(cm, target, false, false, false, loader)

        val absolutePath = target.toAbsolutePath()
        val jarName = file.name.substringAfterLast(File.separator).removeSuffix(".jar")
        val jarPath = absolutePath.resolve("$jarName.jar")
        val builder = JarBuilder("$jarPath", manifest)
        val enumeration = file.entries()

        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement() as JarEntry
            if (entry.isManifest) continue

            if (entry.isClass && pkg.isParent(entry.pkg)) {
                val `class` = cm[entry.name.removeSuffix(".class")]

                if (`class` is ConcreteClass) {
                    val localPath = "${`class`.fullName}.class"
                    val path = "${absolutePath.resolve(localPath)}"

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