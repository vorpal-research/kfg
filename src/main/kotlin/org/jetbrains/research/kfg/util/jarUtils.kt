package org.jetbrains.research.kfg.util

import org.jetbrains.research.kfg.CM
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.builder.asm.ClassBuilder
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.util.CheckClassAdapter
import java.io.*
import java.util.jar.*

fun JarEntry.isClass() = this.name.endsWith(".class")

fun parseJarClasses(jar: JarFile, `package`: Package = Package("*")): Map<String, ClassNode> {
    val classes = mutableMapOf<String, ClassNode>()
    val enumeration = jar.entries()
    while (enumeration.hasMoreElements()) {
        val entry = enumeration.nextElement() as JarEntry

        if (entry.isClass() && `package`.isParent(entry.name)) {
            val classReader = ClassReader(jar.getInputStream(entry))
            val classNode = ClassNode()
            classReader.accept(classNode, ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
            classes[classNode.name] = classNode
        }

    }
    return classes
}

fun writeJar(jar: JarFile, `package`: Package = Package("*"), suffix: String = "instrumented") {
    val jarName = jar.name.substringAfterLast('/').removeSuffix(".jar")
    val builder = JarBuilder("$jarName-$suffix.jar")
    val enumeration = jar.entries()

    for (it in jar.manifest.mainAttributes) {
        builder.addMainAttribute(it.key, it.value)
    }

    for (it in jar.manifest.entries) {
        builder.addManifestEntry(it.key, it.value)
    }

    while (enumeration.hasMoreElements()) {
        val entry = enumeration.nextElement() as JarEntry
        if (entry.name == "META-INF/MANIFEST.MF") continue

        if (entry.isClass() && `package`.isParent(entry.name)) {
            val `class` = CM.getByName(entry.name.removeSuffix(".class"))
            val cn = ClassBuilder(`class`).build()
            val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
            val cca = CheckClassAdapter(cw)
            cn.accept(cca)

            val classFileName = "${`class`.getFullname()}.class"
            val file = File(classFileName)
            file.parentFile.mkdirs()
            val fos = FileOutputStream(file)
            fos.write(cw.toByteArray())
            fos.close()

            val newEntry = JarEntry(file.path.replace("\\", "/"))
            builder.add(newEntry, FileInputStream(file))
        } else {
            builder.add(entry, jar.getInputStream(entry))
        }
    }
    builder.close()
}

class JarBuilder(val name: String) {
    private val manifest = Manifest()
    private var jar: JarOutputStream? = null

    fun addMainAttribute(key: Any, attrs: Any) { manifest.mainAttributes[key] = attrs }
    fun addManifestEntry(key: String, attrs: Attributes) { manifest.entries[key] = attrs }

    fun init() {
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