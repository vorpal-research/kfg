package org.jetbrains.research.kfg.util

import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.KfgException
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.builder.asm.ClassBuilder
import org.jetbrains.research.kfg.ir.Class
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.util.CheckClassAdapter
import java.io.*
import java.net.URLClassLoader
import java.util.jar.*

private fun getCurrentDirectory() = File(".").canonicalPath

private fun setCurrentDirectory(path: String) = setCurrentDirectory(File(path))

private fun setCurrentDirectory(path: File) {
    if (!path.exists()) path.mkdirs()
    require(path.isDirectory)
    System.setProperty("user.dir", path.canonicalPath)
}


fun JarEntry.isClass() = this.name.endsWith(".class")

fun JarFile.getClassLoader() = URLClassLoader(arrayOf(File(this.name).toURI().toURL()))


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

class KfgClassWriter(val loader: ClassLoader, flags: Flags) : ClassWriter(flags.value) {

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
    private var jar: JarOutputStream? = null

    fun addMainAttribute(key: Any, attrs: Any) {
        manifest.mainAttributes[key] = attrs
    }

    fun addManifestEntry(key: String, attrs: Attributes) {
        manifest.entries[key] = attrs
    }

    private fun init() {
        jar = JarOutputStream(FileOutputStream(name), manifest)
    }

    fun add(source: File) {
        if (jar == null) init()
        if (source.isDirectory) {
            var name = source.path.replace("\\", "/")
            if (!name.isEmpty()) {
                if (!name.endsWith("/"))
                    name += "/"
                val entry = JarEntry(name)
                entry.time = source.lastModified()
                jar?.putNextEntry(entry)
                jar?.closeEntry()
            }

        } else {
            val entry = JarEntry(source.path.replace("\\", "/"))
            entry.time = source.lastModified()
            add(entry, FileInputStream(source))
        }
    }

    fun add(entry: JarEntry, fis: InputStream) {
        if (jar == null) init()
        jar?.putNextEntry(entry)
        val `in` = BufferedInputStream(fis)

        val buffer = ByteArray(1024)
        while (true) {
            val count = `in`.read(buffer)
            if (count == -1) break
            jar?.write(buffer, 0, count)
        }
        jar?.closeEntry()
    }

    fun close() {
        jar?.close()
    }
}

object JarUtils {
    private fun readClassNode(input: InputStream, flags: Flags = Flags.readAll): ClassNode {
        val classReader = ClassReader(input)
        val classNode = ClassNode()
        classReader.accept(classNode, flags.value)
        return classNode
    }

    private fun writeClassNode(loader: ClassLoader, cn: ClassNode, filename: String, flags: Flags = Flags.writeComputeAll): File {
        val cw = KfgClassWriter(loader, flags)
        val cca = CheckClassAdapter(cw)
        cn.accept(cca)

        val file = File(filename)
        file.parentFile?.mkdirs()
        val fos = FileOutputStream(file)
        fos.write(cw.toByteArray())
        fos.close()
        return file
    }

    fun parseJarClasses(jar: JarFile, `package`: Package, flags: Flags): Map<String, ClassNode> {
        val classes = mutableMapOf<String, ClassNode>()
        val enumeration = jar.entries()
        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement() as JarEntry

            if (entry.isClass() && `package`.isParent(entry.name)) {
                val classNode = readClassNode(jar.getInputStream(entry), flags)
                classes[classNode.name] = classNode
            }

        }
        return classes
    }

    fun writeClass(loader: ClassLoader, `class`: Class, filename: String = "${`class`.fullname}.class", flags: Flags = Flags.writeComputeFrames) =
            writeClassNode(loader, ClassBuilder(`class`).build(), filename, flags)

    fun writeClasses(jar: JarFile, `package`: Package, writeAllClasses: Boolean = false) {
        val loader = jar.getClassLoader()

        val currentDir = getCurrentDirectory()
        val enumeration = jar.entries()

        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement() as JarEntry
            if (entry.name == "META-INF/MANIFEST.MF") continue

            if (entry.isClass()) {
                if (`package`.isParent(entry.name)) {
                    val `class` = CM.getByName(entry.name.removeSuffix(".class"))
                    val localPath = "${`class`.fullname}.class"
                    val path = "$currentDir/$localPath"
                    writeClass(loader, `class`, path, Flags.writeComputeFrames)
                } else if (writeAllClasses) {
                    val path = "$currentDir/${entry.name}"
                    val classNode = readClassNode(jar.getInputStream(entry))
                    writeClassNode(loader, classNode, path, Flags.writeComputeNone)
                }
            }
        }
    }

    fun writeClassesToTarget(jar: JarFile, target: File, `package`: Package, writeAllClasses: Boolean = false) {
        val workingDir = getCurrentDirectory()
        setCurrentDirectory(target)
        writeClasses(jar, `package`, writeAllClasses)
        setCurrentDirectory(workingDir)
    }

    fun updateJar(jar: JarFile, target: File, `package`: Package): JarFile {
        val workingDir = getCurrentDirectory()
        setCurrentDirectory(target)
        val currentDir = getCurrentDirectory()
        val jarName = jar.name.substringAfterLast('/').removeSuffix(".jar")
        val builder = JarBuilder("$currentDir/$jarName.jar")
        val enumeration = jar.entries()

        for (it in jar.manifest.mainAttributes) {
            builder.addMainAttribute(it.key, it.value)
        }

        for (it in jar.manifest.entries) {
            builder.addManifestEntry(it.key, it.value)
        }
        writeClasses(jar, `package`)

        while (enumeration.hasMoreElements()) {
            val entry = enumeration.nextElement() as JarEntry
            if (entry.name == "META-INF/MANIFEST.MF") continue

            if (entry.isClass() && `package`.isParent(entry.name)) {
                val `class` = CM.getByName(entry.name.removeSuffix(".class"))
                val localPath = "${`class`.fullname}.class"
                val path = "$currentDir/$localPath"

                val newEntry = JarEntry(localPath.replace("\\", "/"))
                builder.add(newEntry, FileInputStream(path))
            } else {
                builder.add(entry, jar.getInputStream(entry))
            }
        }
        builder.close()
        setCurrentDirectory(workingDir)
        return JarFile(builder.name)
    }
}
