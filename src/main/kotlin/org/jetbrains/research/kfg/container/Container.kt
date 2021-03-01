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

    val commonPackage: Package

    fun parse(flags: Flags, failOnError: Boolean = false, loader: ClassLoader = classLoader): Map<String, ClassNode>
    fun unpack(cm: ClassManager, target: Path, unpackAllClasses: Boolean = false, failOnError: Boolean = false, loader: ClassLoader = classLoader)
    fun update(cm: ClassManager, loader: ClassLoader = classLoader) = update(cm, Files.createTempDirectory("kfg"), loader)
    fun update(cm: ClassManager, target: Path, loader: ClassLoader = classLoader): Container
}

fun File.asContainer(pkg: Package? = null): Container? = when {
    this.isJar -> JarContainer(this.toPath(), pkg)
    this.isDirectory -> DirectoryContainer(this, pkg)
    else -> null
}

fun Path.asContainer(pkg: Package? = null) = this.toFile().asContainer(pkg)