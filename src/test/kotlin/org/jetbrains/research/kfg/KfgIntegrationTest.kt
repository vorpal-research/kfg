package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.util.writeJar
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.jar.JarFile

import org.junit.Before as before
import org.junit.After as after
import org.junit.Test as test
import kotlin.test.assertTrue
import java.io.File

internal fun deleteDirectory(directory: File): Boolean {
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
    val out = ByteArrayOutputStream()
    val err = ByteArrayOutputStream()

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
        val jar = "target/kfg-0.1-jar-with-dependencies.jar"
        val `package` = Package("org/jetbrains/research/kfg/*")
        val target = File("instrumented/")

        val jarFile = JarFile(jar)
        CM.parseJar(jarFile, `package`)
        writeJar(jarFile, target, `package`)

        assertTrue(deleteDirectory(target), "could not delete directory")
        assertTrue(out.toString().isBlank(), out.toString())
        assertTrue(err.toString().isBlank(), err.toString())
    }
}