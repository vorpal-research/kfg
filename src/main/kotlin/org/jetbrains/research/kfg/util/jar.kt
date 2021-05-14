package org.jetbrains.research.kfg.util

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.KfgException
import org.jetbrains.research.kfg.builder.asm.ClassBuilder
import org.jetbrains.research.kfg.builder.cfg.LabelFilterer
import org.jetbrains.research.kfg.ir.Class
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.JSRInlinerAdapter
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FrameNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.util.CheckClassAdapter
import java.io.*
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

val JarEntry.isClass get() = this.name.endsWith(".class")
val JarEntry.className get() = this.name.removeSuffix(".class")
val JarEntry.isManifest get() = this.name == "META-INF/MANIFEST.MF"

val JarFile.classLoader get() = File(this.name).classLoader

val ClassNode.hasFrameInfo: Boolean get() {
    var hasInfo = false
    for (mn in methods) {
        hasInfo = hasInfo || mn.instructions.any { it is FrameNode }
    }
    return hasInfo
}

internal fun ClassNode.inlineJsrs() {
    this.methods = methods.map { it.jsrInlined }
}

internal val MethodNode.jsrInlined: MethodNode
    get() {
        val temp = JSRInlinerAdapter(this, access, name, desc, signature, exceptions?.toTypedArray())
        this.accept(temp)
        return LabelFilterer(temp).build()
    }

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
        val writeComputeAll = writeComputeFrames
    }

    fun merge(other: Flags) = Flags(this.value or other.value)
    operator fun plus(other: Flags) = this.merge(other)

    override fun compareTo(other: Flags) = value.compareTo(other.value)
}

class KfgClassWriter(private val loader: ClassLoader, flags: Flags) : ClassWriter(flags.value) {

    private fun readClass(type: String) = try {
        java.lang.Class.forName(type.replace('/', '.'), false, loader)
    } catch (e: Throwable) {
        throw ClassReadError(e.toString())
    }

    override fun getCommonSuperClass(type1: String, type2: String): String = try {
        var class1 = readClass(type1)
        val class2 = readClass(type2)

        when {
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
    } catch (e: Throwable) {
        "java/lang/Object"
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

internal fun ClassNode.recomputeFrames(loader: ClassLoader): ClassNode {
    val ba = this.toByteArray(loader)
    return ba.toClassNode()
}

private fun ByteArray.toClassNode(): ClassNode {
    val classReader = ClassReader(this.inputStream())
    val classNode = ClassNode()
    classReader.accept(classNode, Flags.readAll.value)
    return classNode
}

private fun ClassNode.toByteArray(loader: ClassLoader, flags: Flags = Flags.writeComputeAll): ByteArray {
    this.inlineJsrs()
    val cw = KfgClassWriter(loader, flags)
    val cca = CheckClassAdapter(cw)
    this.accept(cca)
    return cw.toByteArray()
}

internal fun ClassNode.write(loader: ClassLoader,
                             filename: String,
                             flags: Flags = Flags.writeComputeAll): File =
        File(filename).apply {
            parentFile?.mkdirs()
            FileOutputStream(this).use { fos ->
                fos.write(this@write.toByteArray(loader, flags))
            }
        }

fun Class.write(cm: ClassManager, loader: ClassLoader,
                filename: String = "$fullName.class",
                flags: Flags = Flags.writeComputeFrames): File =
        ClassBuilder(cm, this).build().write(loader, filename, flags)
