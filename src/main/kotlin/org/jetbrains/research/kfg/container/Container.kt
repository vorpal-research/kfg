package org.jetbrains.research.kfg.container

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.util.Flags
import org.jetbrains.research.kfg.util.isClass
import org.jetbrains.research.kfg.util.isJar
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

interface Container {
    val name: String
    val pkg: Package
    val classLoader: ClassLoader

    fun parse(flags: Flags): Map<String, ClassNode>
    fun unpack(cm: ClassManager, target: Path, unpackAllClasses: Boolean = false)
    fun update(cm: ClassManager) = update(cm, Files.createTempDirectory("kfg"))
    fun update(cm: ClassManager, target: Path): Container
}

fun File.asContainer(pkg: Package): Container? = when {
    this.isJar -> JarContainer(this.toPath(), pkg)
    this.isDirectory -> DirectoryContainer(this, pkg)
    else -> null
}

fun Path.asContainer(pkg: Package) = this.toFile().asContainer(pkg)