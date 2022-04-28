package org.vorpal.research.kfg.container

import org.objectweb.asm.tree.ClassNode
import org.vorpal.research.kfg.util.Flags
import org.vorpal.research.kfg.util.isJar
import java.io.File
import java.nio.file.Path

interface Container {
    val name: String
    val pkg: org.vorpal.research.kfg.Package
    val classLoader: ClassLoader
    val path: Path

    val commonPackage: org.vorpal.research.kfg.Package

    fun parse(flags: Flags, failOnError: Boolean = false, loader: ClassLoader = classLoader): Map<String, ClassNode>
    fun unpack(
        cm: org.vorpal.research.kfg.ClassManager,
        target: Path,
        unpackAllClasses: Boolean = false,
        failOnError: Boolean = false,
        checkClass: Boolean = false,
        loader: ClassLoader = classLoader
    )

    fun update(cm: org.vorpal.research.kfg.ClassManager, target: Path, loader: ClassLoader = classLoader): Container
}

fun File.asContainer(pkg: org.vorpal.research.kfg.Package? = null): Container? = when {
    this.isJar -> JarContainer(this.toPath(), pkg)
    this.isDirectory -> DirectoryContainer(this, pkg)
    else -> null
}

fun Path.asContainer(pkg: org.vorpal.research.kfg.Package? = null) = this.toFile().asContainer(pkg)