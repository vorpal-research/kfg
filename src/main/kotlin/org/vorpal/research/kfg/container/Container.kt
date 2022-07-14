package org.vorpal.research.kfg.container

import org.objectweb.asm.tree.ClassNode
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.util.Flags
import org.vorpal.research.kfg.util.isJar
import java.io.File
import java.nio.file.Path

interface Container {
    val name: String
    val pkg: Package
    val classLoader: ClassLoader
    val path: Path

    val commonPackage: Package

    fun parse(flags: Flags, failOnError: Boolean = false, loader: ClassLoader = classLoader): Map<String, ClassNode>
    fun unpack(
        cm: ClassManager,
        target: Path,
        unpackAllClasses: Boolean = false,
        failOnError: Boolean = false,
        checkClass: Boolean = false,
        loader: ClassLoader = classLoader
    )

    fun update(cm: ClassManager, target: Path, loader: ClassLoader = classLoader): Container

    fun extract(target: Path)
}

fun File.asContainer(pkg: Package? = null): Container? = when {
    this.isJar -> JarContainer(this.toPath(), pkg)
    this.isDirectory -> DirectoryContainer(this, pkg)
    else -> null
}

fun Path.asContainer(pkg: Package? = null) = this.toFile().asContainer(pkg)