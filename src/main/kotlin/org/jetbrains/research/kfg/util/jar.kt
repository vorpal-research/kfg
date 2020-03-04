package org.jetbrains.research.kfg.util

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.KfgException
import org.jetbrains.research.kfg.builder.asm.ClassBuilder
import org.jetbrains.research.kfg.ir.Class
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.util.CheckClassAdapter
import java.io.*
import java.net.URLClassLoader
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

val JarEntry.isClass get() = this.name.endsWith(".class")
val JarEntry.className get() = this.name.removeSuffix(".class")
val JarEntry.isManifest get() = this.name == "META-INF/MANIFEST.MF"

val JarFile.classLoader get() = URLClassLoader(arrayOf(File(this.name).toURI().toURL()))

class ClassReadError(msg: String) : KfgException(msg)

data class Flags(val value: Int) : Comparable<Flags> {
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

    override fun compareTo(other: Flags) = value.compareTo(other.value)
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

class JarBuilder(val name: String, manifest: Manifest) {
    private val jar = JarOutputStream(FileOutputStream(name), manifest)

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

internal fun readClassNode(input: InputStream, flags: Flags = Flags.readAll): ClassNode {
    val classReader = ClassReader(input)
    val classNode = ClassNode()
    classReader.accept(classNode, flags.value)
    return classNode
}

internal fun ClassNode.write(loader: ClassLoader,
                            filename: String,
                            flags: Flags = Flags.writeComputeAll): File {
    val cw = KfgClassWriter(loader, flags)
    val cca = CheckClassAdapter(cw)
    this.accept(cca)

    return File(filename).apply {
        parentFile?.mkdirs()
    }.also {
        FileOutputStream(it).use { fos ->
            fos.write(cw.toByteArray())
        }
    }
}

fun Class.write(cm: ClassManager, loader: ClassLoader,
                filename: String = "$fullname.class",
                flags: Flags = Flags.writeComputeFrames): File =
        ClassBuilder(cm, this).build().write(loader, filename, flags)
