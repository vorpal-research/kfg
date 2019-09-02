package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.util.updateJar
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.util.jar.JarFile
import kotlin.test.assertTrue
import org.junit.After as after
import org.junit.Before as before
import org.junit.Test as test

private fun deleteDirectory(directory: File): Boolean {
    if (directory.exists()) {
        val files = directory.listFiles()
        if (null != files) {
            for (i in files.indices) {
                if (files[i].isDirectory) {
                    deleteDirectory(files[i])
                } else {
                    files[i].delete()
                }
            }
        }
    }
    return directory.delete()
}

// simple test: run kfg on itself and check nothing fails
class KfgIntegrationTest {
    private val out = ByteArrayOutputStream()
    private val err = ByteArrayOutputStream()

    @before
    fun setUp() {
        System.setOut(PrintStream(out))
        System.setErr(PrintStream(err))
    }

    @after
    fun tearDown() {
        System.setOut(System.out)
        System.setErr(System.err)
    }

    @test
    fun run() {
        val version = System.getProperty("project.version")
        val jar = "target/kfg-$version-jar-with-dependencies.jar"
        val `package` = Package("org/jetbrains/research/kfg/*")
        val target = Files.createTempDirectory("kfg").toFile()

        val jarFile = JarFile(jar)
        val cm = ClassManager(jarFile, `package`)
        updateJar(cm, jarFile, `package`, target)

        assertTrue(deleteDirectory(target), "could not delete directory")
        assertTrue(out.toString().isBlank(), out.toString())
        assertTrue(err.toString().isBlank(), err.toString())
    }
}