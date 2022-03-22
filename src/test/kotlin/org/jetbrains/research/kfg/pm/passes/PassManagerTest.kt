package org.jetbrains.research.kfg.pm.passes

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.KfgConfigBuilder
import org.jetbrains.research.kfg.Package
import org.jetbrains.research.kfg.container.JarContainer
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.visitor.*
import org.jetbrains.research.kfg.visitor.pass.PassManager
import org.jetbrains.research.kfg.visitor.pass.strategy.iterativeastar.IterativeAStarPlusPassStrategy
import org.jetbrains.research.kfg.visitor.pass.strategy.topologic.DefaultPassStrategy
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.*


class PassManagerTest {
    private val out = ByteArrayOutputStream()
    private val err = ByteArrayOutputStream()

    val pkg = Package.parse("org.jetbrains.research.kfg.*")
    lateinit var jar: JarContainer
    lateinit var cm: ClassManager

    @BeforeTest
    fun setUp() {
        //System.setOut(PrintStream(out))
        //System.setErr(PrintStream(err))

        val version = System.getProperty("project.version")
        val jarPath = "target/kfg-$version-jar-with-dependencies.jar"

        jar = JarContainer(jarPath, pkg)
        cm = ClassManager(
            KfgConfigBuilder()
                .failOnError(false)
                .build()
        )
        cm.initialize(jar)
    }

    @AfterTest
    fun tearDown() {
        //System.setOut(System.out)
        //System.setErr(System.err)
    }

    @Test
    fun passManagerPipelineTest() {
        val pm = PassManager(IterativeAStarPlusPassStrategy())

        val klass = run {
            var temp = cm.concreteClasses.random()
            while (temp.methods.isEmpty())
                temp = cm.concreteClasses.random()
            temp
        }
        val targetMethod = klass.getMethods(klass.methods.random().name).filterIndexed {index, _ -> index < 1}

        val provider = TestProvider()

        executePipeline(cm, targetMethod) {
            passManager = pm
            schedule<P2>()
            schedule<P8>()
            schedule<P10>()
            schedule<P12>()
            schedule<P14>()
            schedule<P16>()
            registerProvider(provider)
        }

        val context = provider.provide()

        assertEquals(16, context.executedPasses.size)
        println(context.executedAnalysis.size)
    }

}