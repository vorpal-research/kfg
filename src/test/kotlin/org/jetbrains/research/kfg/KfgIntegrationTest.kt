package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.container.DirectoryContainer
import org.jetbrains.research.kfg.container.JarContainer
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.visitor.ClassVisitor
import org.jetbrains.research.kfg.visitor.executePipeline
import org.jetbrains.research.kthelper.logging.log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import kotlin.test.*

// simple test: run kfg on itself and check nothing fails
class KfgIntegrationTest {
    private val out = ByteArrayOutputStream()
    private val err = ByteArrayOutputStream()

    val pkg = Package("org/jetbrains/research/kfg/*")
    lateinit var jar: JarContainer
    lateinit var cm: ClassManager

    @BeforeTest
    fun setUp() {
        System.setOut(PrintStream(out))
        System.setErr(PrintStream(err))

        val version = System.getProperty("project.version")
        val jarPath = "target/kfg-$version-jar-with-dependencies.jar"
        val `package` = Package("org/jetbrains/research/kfg/*")

        jar = JarContainer(jarPath, `package`)
        cm = ClassManager(
            KfgConfigBuilder()
                .failOnError(false)
                .build()
        )
        cm.initialize(jar)
    }

    @AfterTest
    fun tearDown() {
        System.setOut(System.out)
        System.setErr(System.err)
    }

    @Test
    fun run() {
        val target = Files.createTempDirectory(Paths.get("."), "kfg")
        jar.update(cm, target)

        assertTrue(out.toString().isBlank(), out.toString())
        assertTrue(err.toString().isBlank(), err.toString())
        if (!target.toFile().deleteRecursively()) {
            log.warn("Could not delete temp directory ${target.toAbsolutePath()}")
        }
    }

    @Test
    fun directoryContainerTest() {
        val targetDirPath = Paths.get("./target/classes")
        val `package` = Package("org/jetbrains/research/kfg/*")

        val container = DirectoryContainer(targetDirPath, `package`)
        val cm = ClassManager(
            KfgConfigBuilder()
                .failOnError(false)
                .build()
        )
        cm.initialize(container)


        val target = Files.createTempDirectory("kfg")
        container.update(cm, target)

        assertTrue(out.toString().isBlank(), out.toString())
        assertTrue(err.toString().isBlank(), err.toString())
        if (!target.toFile().deleteRecursively()) {
            log.warn("Could not delete temp directory ${target.toAbsolutePath()}")
        }
    }

    @Test
    fun packagePipelineTest() {
        val visitedClasses = mutableSetOf<Class>()
        executePipeline(cm, pkg) {
            +object : ClassVisitor {
                override val cm: ClassManager
                    get() = this@KfgIntegrationTest.cm

                override fun cleanup() {}

                override fun visit(klass: Class) {
                    super.visit(klass)
                    visitedClasses += klass
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

                override fun visit(klass: Class) {
                    super.visit(klass)
                    visitedClasses += klass
                }
            }
        }

        assertEquals(targetClasses.intersect(visitedClasses), targetClasses)
        assertTrue((targetClasses - visitedClasses).isEmpty())
    }

    @Test
    fun methodPipelineTest() {
        val klass = run {
            var temp = cm.concreteClasses.random()
            while (temp.methods.isEmpty())
                temp = cm.concreteClasses.random()
            temp
        }
        val targetMethods = klass.getMethods(klass.methods.random().name)

        val visitedMethods = mutableSetOf<Method>()
        executePipeline(cm, targetMethods) {
            +object : ClassVisitor {
                override val cm: ClassManager
                    get() = this@KfgIntegrationTest.cm

                override fun cleanup() {}

                override fun visitMethod(method: Method) {
                    super.visitMethod(method)
                    visitedMethods += method
                }
            }
        }

        assertEquals(targetMethods.intersect(visitedMethods), targetMethods)
        assertTrue((targetMethods - visitedMethods).isEmpty())
    }
}