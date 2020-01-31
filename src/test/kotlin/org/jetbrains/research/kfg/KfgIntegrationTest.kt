package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.util.update
import org.jetbrains.research.kfg.visitor.ClassVisitor
import org.jetbrains.research.kfg.visitor.executePipeline
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.util.*
import java.util.jar.JarFile
import kotlin.test.*

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

    val `package` = Package("org/jetbrains/research/kfg/*")
    lateinit var jarFile: JarFile
    lateinit var cm: ClassManager

    @BeforeTest
    fun setUp() {
        System.setOut(PrintStream(out))
        System.setErr(PrintStream(err))

        val version = System.getProperty("project.version")
        val jar = "target/kfg-$version-jar-with-dependencies.jar"
        val `package` = Package("org/jetbrains/research/kfg/*")

        jarFile = JarFile(jar)
        cm = ClassManager(jarFile, KfgConfigBuilder().`package`(`package`).build())
    }

    @AfterTest
    fun tearDown() {
        System.setOut(System.out)
        System.setErr(System.err)
    }

    @Test
    fun run() {
        val target = Files.createTempDirectory("kfg")
        jarFile.update(cm, `package`, target)

        assertTrue(deleteDirectory(target.toFile()), "could not delete directory")
        assertTrue(out.toString().isBlank(), out.toString())
        assertTrue(err.toString().isBlank(), err.toString())
    }

    @Test
    fun packagePipelineTest() {
        val visitedClasses = mutableSetOf<Class>()
        executePipeline(cm, `package`) {
            +object : ClassVisitor {
                override val cm: ClassManager
                    get() = this@KfgIntegrationTest.cm

                override fun cleanup() {}

                override fun visit(`class`: Class) {
                    super.visit(`class`)
                    visitedClasses += `class`
                }
            }
        }

        assertEquals(cm.concreteClasses.intersect(visitedClasses), cm.concreteClasses)
        assertTrue((cm.concreteClasses - visitedClasses).isEmpty())
    }

    @Test
    fun classPipelineTest() {
        val klass = cm.concreteClasses.random()
        val targetClasses = run {
            val result = mutableSetOf<Class>(klass)
            val queue = ArrayDeque<Class>(listOf(klass))
            while (queue.isNotEmpty()) {
                val first = queue.pollFirst()
                result += first
                queue.addAll(first.innerClasses.filterNot { it in result })
            }
            result
        }

        val visitedClasses = mutableSetOf<Class>()
        executePipeline(cm, klass) {
            +object : ClassVisitor {
                override val cm: ClassManager
                    get() = this@KfgIntegrationTest.cm

                override fun cleanup() {}

                override fun visit(`class`: Class) {
                    super.visit(`class`)
                    visitedClasses += `class`
                }
            }
        }

        assertEquals(targetClasses.intersect(visitedClasses), targetClasses)
        assertTrue((targetClasses - visitedClasses).isEmpty())
    }
}