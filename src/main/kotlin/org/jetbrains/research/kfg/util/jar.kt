package org.jetbrains.research.kfg.util

import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.builder.asm.ClassBuilder
import org.jetbrains.research.kfg.ir.Class
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.util.CheckClassAdapter
import java.io.*
import java.util.jar.*

internal fun getCurrentDirectory() = File(".").canonicalPath

internal fun setCurrentDirectory(path: String) = setCurrentDirectory(File(path))

internal fun setCurrentDirectory(path: File) {
    if (!path.exists()) path.mkdirs()
    assert(path.isDirectory)
    System.setProperty("user.dir", path.canonicalPath)
}

internal fun JarEntry.isClass() = this.name.endsWith(".class")

fun readClassNode(input: InputStream, skipDebug: Boolean = true): ClassNode {
    val classReader = ClassReader(input)
    val classNode = ClassNode()
    classReader.accept(classNode, if (skipDebug) ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES else 0)
    return classNode
}

fun writeClassNode(cn: ClassNode, filename: String, computeFrames: Boolean = true): File {
    val cw = ClassWriter(if (computeFrames) ClassWriter.COMPUTE_FRAMES else 0)
    val cca = CheckClassAdapter(cw)
    cn.accept(cca)

    val file = File(filename)
    file.parentFile?.mkdirs()
    val fos = FileOutputStream(file)
    fos.write(cw.toByteArray())
    fos.close()
    return file
}

fun parseJarClasses(jar: JarFile, `package`: Package): Map<String, ClassNode> {
    val classes = mutableMapOf<String, ClassNode>()
    val enumeration = jar.entries()
    while (enumeration.hasMoreElements()) {
        val entry = enumeration.nextElement() as JarEntry

        if (entry.isClass() && `package`.isParent(entry.name)) {
            val classNode = readClassNode(jar.getInputStream(entry))
            classes[classNode.name] = classNode
        }

    }
    return classes
}

fun writeClass(`class`: Class, filename: String = "${`class`.getFullname()}.class")
        = writeClassNode(ClassBuilder(`class`).build(), filename)

fun writeClasses(jar: JarFile, `package`: Package, writeAllClasses: Boolean = false) {
    val currentDir = getCurrentDirectory()
    val enumeration = jar.entries()

    while (enumeration.hasMoreElements()) {
        val entry = enumeration.nextElement() as JarEntry
        if (entry.name == "META-INF/MANIFEST.MF") continue

        if (entry.isClass()) {
            if (`package`.isParent(entry.name)) {
                val `class` = CM.getByName(entry.name.removeSuffix(".class"))
                val localPath = "${`class`.getFullname()}.class"
                val path = "$currentDir/$localPath"
                writeClass(`class`, path)
            } else if (writeAllClasses) {
                val path = "$currentDir/${entry.name}"
                val classNode = readClassNode(jar.getInputStream(entry), false)
                writeClassNode(classNode, path, false)
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

fun writeJar(jar: JarFile, target: File, `package`: Package): JarFile {
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
            val localPath = "${`class`.getFullname()}.class"
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