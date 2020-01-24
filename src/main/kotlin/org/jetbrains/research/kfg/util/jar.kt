package org.jetbrains.research.kfg.util

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.KfgException
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.builder.asm.ClassBuilder
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.ConcreteClass
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.util.CheckClassAdapter
import java.io.*
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.*

val JarEntry.isClass get() = this.name.endsWith(".class")
val JarEntry.className get() = this.name.removeSuffix(".class")
val JarEntry.isManifest get() = this.name == "META-INF/MANIFEST.MF"

val JarFile.classLoader get() = URLClassLoader(arrayOf(File(this.name).toURI().toURL()))

class ClassReadError(msg: String) : KfgException(msg)

data class Flags(val value: Int) {
    companion object {
        val readAll = Flags(0)
        val readSkipDebug = Flags(ClassReader.SKIP_DEBUG)
        val readSkipFrames = Flags(ClassReader.SKIP_FRAMES)
        val readCodeOnly = readSkipDebug + readSkipFrames

        val writeComputeNone = Flags(0)
        val writeComputeFrames = Flags(ClassWriter.COMPUTE_FRAMES)
        val writeComputeMaxs = Flags(ClassWriter.COMPUTE_MAXS)
        val writeComputeAll = writeComputeFrames + writeComputeMaxs
    }

    fun merge(other: Flags) = Flags(this.value or other.value)
    operator fun plus(other: Flags) = this.merge(other)
}

class KfgClassWriter(private val loader: ClassLoader, flags: Flags) : ClassWriter(flags.value) {

    private fun readClass(type: String) = try {
        java.lang.Class.forName(type.replace('/', '.'), false, loader)
    } catch (e: Exception) {
        throw ClassReadError(e.toString())
    }

    override fun getCommonSuperClass(type1: String, type2: String): String {
        var class1 = readClass(type1)
        val class2 = readClass(type2)

        return when {
            class1.isAssignableFrom(class2) -> type1
            class2.isAssignableFrom(class1) -> type2
            class1.isInterface || class2.isInterface -> "java/lang/Object"
            else -> {
                do {
                    class1 = class1.superclass
                } while (!class1.isAssignableFrom(class2))
                class1.name.replace('.', '/')
            }
        }
    }
}

class JarBuilder(val name: String) {
    private val manifest = Manifest()
    private lateinit var jar: JarOutputStream

    fun addMainAttribute(key: Any, attrs: Any) {
        manifest.mainAttributes[key] = attrs
    }

    fun addManifestEntry(key: String, attrs: Attributes) {
        manifest.entries[key] = attrs
    }

    /**
     * Initializes jar file output stream. Should be called only after manifest file is configured
     */
    fun init() {
        jar = JarOutputStream(FileOutputStream(name), manifest)
    }

    fun add(source: File) {
        if (source.isDirectory) {
            var name = source.path.replace("\\", "/")
            if (name.isNotEmpty()) {
                if (!name.endsWith("/"))
                    name += "/"
                val entry = JarEntry(name)
                entry.time = source.lastModified()
                jar.putNextEntry(entry)
                jar.closeEntry()
            }

        } else {
            val entry = JarEntry(source.path.replace("\\", "/"))
            entry.time = source.lastModified()
            add(entry, FileInputStream(source))
        }
    }

    operator fun plusAssign(source: File) {
        add(source)
    }

    fun add(entry: JarEntry, fis: InputStream) {
        jar.putNextEntry(entry)
        val `in` = BufferedInputStream(fis)

        val buffer = ByteArray(1024)
        while (true) {
            val count = `in`.read(buffer)
            if (count == -1) break
            jar.write(buffer, 0, count)
        }
        jar.closeEntry()
    }

    fun close() {
        jar.close()
    }
}

private fun readClassNode(input: InputStream, flags: Flags = Flags.readAll): ClassNode {
    val classReader = ClassReader(input)
    val classNode = ClassNode()
    classReader.accept(classNode, flags.value)
    return classNode
}

private fun ClassNode.write(loader: ClassLoader,
                           filename: String,
                           flags: Flags = Flags.writeComputeAll): File {
    val cw = KfgClassWriter(loader, flags)
    val cca = CheckClassAdapter(cw)
    this.accept(cca)

    val file = File(filename).apply { parentFile?.mkdirs() }
    val fos = FileOutputStream(file)
    fos.write(cw.toByteArray())
    fos.close()
    return file
}

fun Class.write(cm: ClassManager, loader: ClassLoader,
               filename: String = "$fullname.class",
               flags: Flags = Flags.writeComputeFrames): File =
        ClassBuilder(cm, this).build().write(loader, filename, flags)

fun JarFile.unpack(cm: ClassManager, target: Path, `package`: Package, unpackAllClasses: Boolean = false) {
    val loader = this.classLoader

    val absolutePath = target.toAbsolutePath()
    val enumeration = this.entries()

    while (enumeration.hasMoreElements()) {
        val entry = enumeration.nextElement() as JarEntry
        if (entry.isManifest) continue

        if (entry.isClass) {
            val `class` = cm.getByName(entry.name.removeSuffix(".class"))
            when {
                `package`.isParent(entry.name) && `class` is ConcreteClass -> {
                    val localPath = "${`class`.fullname}.class"
                    val path = "$absolutePath/$localPath"
                    `class`.write(cm, loader, path, Flags.writeComputeFrames)
                }
                unpackAllClasses -> {
                    val path = "$absolutePath/${entry.name}"
                    val classNode = readClassNode(this.getInputStream(entry))
                    classNode.write(loader, path, Flags.writeComputeNone)
                }
            }
        }
    }
}

fun JarFile.update(cm: ClassManager, `package`: Package) =
        this.update(cm, `package`, Files.createTempDirectory("kfg"))

fun JarFile.update(cm: ClassManager, `package`: Package, target: Path): JarFile {
    val absolutePath = target.toAbsolutePath()
    val jarName = this.name.substringAfterLast('/').removeSuffix(".jar")
    val builder = JarBuilder("$absolutePath/$jarName.jar")
    val enumeration = this.entries()

    for ((key, value) in this.manifest.mainAttributes) {
        builder.addMainAttribute(key, value)
    }

    for ((key, value) in this.manifest.entries) {
        builder.addManifestEntry(key, value)
    }
    builder.init()

    this.unpack(cm, target, `package`)

    while (enumeration.hasMoreElements()) {
        val entry = enumeration.nextElement() as JarEntry
        if (entry.isManifest) continue

        if (entry.isClass && `package`.isParent(entry.name)) {
            val `class` = cm.getByName(entry.name.removeSuffix(".class"))

            if (`class` is ConcreteClass) {
                val localPath = "${`class`.fullname}.class"
                val path = "$absolutePath/$localPath"

                val newEntry = JarEntry(localPath.replace("\\", "/"))
                builder.add(newEntry, FileInputStream(path))
            } else {
                builder.add(entry, this.getInputStream(entry))
            }
        } else {
            builder.add(entry, this.getInputStream(entry))
        }
    }
    builder.close()
    return JarFile(builder.name)
}

fun JarFile.parse(pack: Package, flags: Flags): Map<String, ClassNode> {
    val classes = mutableMapOf<String, ClassNode>()
    val enumeration = this.entries()
    while (enumeration.hasMoreElements()) {
        val entry = enumeration.nextElement() as JarEntry

        if (entry.isClass && pack.isParent(entry.className)) {
            val classNode = readClassNode(this.getInputStream(entry), flags)
            classes[classNode.name] = classNode
        }

    }
    return classes
}
