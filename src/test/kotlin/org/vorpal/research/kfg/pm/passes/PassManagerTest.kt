package org.vorpal.research.kfg.pm.passes

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.KfgConfigBuilder
import org.vorpal.research.kfg.Package
import org.vorpal.research.kfg.container.JarContainer
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.visitor.executePipeline
import org.vorpal.research.kfg.visitor.getProvider
import org.vorpal.research.kfg.visitor.pass.IllegalPipelineException
import org.vorpal.research.kfg.visitor.pass.PassManager
import org.vorpal.research.kfg.visitor.pass.strategy.iterativeastar.IterativeAStarPlusPassStrategy
import org.vorpal.research.kfg.visitor.schedule
import java.io.ByteArrayOutputStream
import java.io.Serializable
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals


class PassManagerTest {
    private val out = ByteArrayOutputStream()
    private val err = ByteArrayOutputStream()

    private val START_FROM = 20
    private val DATASET_COUNT = 25
    private val PASSES_COUNT = 10
    private val ANALYSIS_COUNT = 20
    private val ROOT_CHANCE = 0.2f
    private val CONNECTEDNESS = 1f
    private val ANALYSIS_REQUIRED = 0.6f
    private val ANALYSIS_PERSISTED = 0.4f

    val pkg = Package.parse("org.vorpal.research.kfg.*")
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
    fun testGeneratedPass() {
        val klass = run {
            var temp = cm.concreteClasses.random()
            while (temp.methods.isEmpty())
                temp = cm.concreteClasses.random()
            temp
        }
        val targetMethod = klass.getMethods(klass.methods.random().name).filterIndexed {index, _ -> index < 1}

        val pm = PassManager(IterativeAStarPlusPassStrategy())
        val provider = TestProvider()

        val nodes = generateTestData()
        val runner = generatePassesAndRunner(nodes)

        runner.execute(pm, provider, cm, targetMethod)

        val context = provider.context

        assertEquals(nodes.size, context.executedPasses.size)
        println(context.executedAnalysis.size)
    }

    @Test
    fun passManagerPipelineTest() {
        val klass = run {
            var temp = cm.concreteClasses.random()
            while (temp.methods.isEmpty())
                temp = cm.concreteClasses.random()
            temp
        }
        val targetMethod = klass.getMethods(klass.methods.random().name).filterIndexed {index, _ -> index < 1}

        val pm = PassManager(IterativeAStarPlusPassStrategy())
        val provider = TestProvider()

        val pipeline = executePipeline(cm, targetMethod) {
            passManager = pm
            schedule<P2>()
            schedule<P8>()
            schedule<P10>()
            schedule<P12>()
            schedule<P14>()
            schedule<P16>()
            registerProvider(provider)
        }

        val context = provider.context
        val executedPasses = pipeline.getProvider<TestProviderInternal>().dummyData

        assertEquals(16, context.executedPasses.size)
        assertEquals(16, executedPasses)
        println(context.executedAnalysis.size)
    }

    @Test(IllegalPipelineException::class)
    fun passManagerCheckProviderIsMissing() {
        val klass = run {
            var temp = cm.concreteClasses.random()
            while (temp.methods.isEmpty())
                temp = cm.concreteClasses.random()
            temp
        }
        val targetMethod = klass.getMethods(klass.methods.random().name).filterIndexed {index, _ -> index < 1}

        val pm = PassManager(IterativeAStarPlusPassStrategy())

        executePipeline(cm, targetMethod) {
            passManager = pm
            schedule<P2>()
        }
    }

    @Test(IllegalPipelineException::class)
    fun passManagerCheckCircularDependency() {
        val klass = run {
            var temp = cm.concreteClasses.random()
            while (temp.methods.isEmpty())
                temp = cm.concreteClasses.random()
            temp
        }
        val targetMethod = klass.getMethods(klass.methods.random().name).filterIndexed {index, _ -> index < 1}

        val pm = PassManager(IterativeAStarPlusPassStrategy())

        val provider = TestProvider()
        executePipeline(cm, targetMethod) {
            passManager = pm
            schedule<P7Circular>()
            registerProvider(provider)
        }
    }

    @Test(IllegalPipelineException::class)
    fun passManagerCheckCircularAnalysisDependency() {
        val klass = run {
            var temp = cm.concreteClasses.random()
            while (temp.methods.isEmpty())
                temp = cm.concreteClasses.random()
            temp
        }
        val targetMethod = klass.getMethods(klass.methods.random().name).filterIndexed {index, _ -> index < 1}

        val pm = PassManager(IterativeAStarPlusPassStrategy())

        val provider = TestProvider()
        executePipeline(cm, targetMethod) {
            passManager = pm
            schedule<P3CircularAnalysis>()
            registerProvider(provider)
        }
    }

    private fun generateTestData(): List<DependencyNodeWrapper> {
        val rng = Random(System.currentTimeMillis())

        val nodes = mutableListOf<DependencyNodeWrapper>()
        val analysis = List(ANALYSIS_COUNT) { index -> (PASSES_COUNT + index).toString() }.toMutableList()

        nodes.add(DependencyNodeWrapper(0.toString(), emptyList(), emptyList(), emptyList()))

        repeat(PASSES_COUNT - 1) { index ->
            val isRoot = rng.nextFloat() <= ROOT_CHANCE

            nodes.shuffle()
            val requiredPasses = mutableListOf<String>()
            if (!isRoot) {
                repeat(rng.nextInt((nodes.size * CONNECTEDNESS).toInt())) { indexRequired ->
                    requiredPasses.add(nodes[indexRequired].name)
                }
            }

            analysis.shuffle()
            val requiredAnalysis = mutableListOf<String>()
            repeat(rng.nextInt((analysis.size * CONNECTEDNESS * ANALYSIS_REQUIRED).toInt())) { indexRequired ->
                requiredAnalysis.add(analysis[indexRequired])
            }

            analysis.shuffle()
            val persistedAnalysis = mutableListOf<String>()
            repeat(rng.nextInt((analysis.size * CONNECTEDNESS * ANALYSIS_PERSISTED).toInt())) { indexPersisted ->
                persistedAnalysis.add(analysis[indexPersisted])
            }

            nodes.add(
                DependencyNodeWrapper(
                    (index + 1).toString(),
                    requiredPasses,
                    requiredAnalysis,
                    persistedAnalysis
                )
            )
        }

        return nodes
    }

    private fun generatePassesAndRunner(nodes: List<DependencyNodeWrapper>): TestPassRunnerTemplateI {
        val passImportsTemplate = this.javaClass
            .classLoader
            ?.getResourceAsStream("passManagerTest/passTemplate")
            ?.bufferedReader()
            ?.readText()
            ?: throw AssertionError("Cannot load pass template")

        val passTemplate = this.javaClass
            .classLoader
            ?.getResourceAsStream("passManagerTest/testPassTemplate")
            ?.bufferedReader()
            ?.readText()
            ?: throw AssertionError("Cannot load pass template")

        val analysisTemplate = this.javaClass
            .classLoader
            ?.getResourceAsStream("passManagerTest/testAnalysisTemplate")
            ?.bufferedReader()
            ?.readText()
            ?: throw AssertionError("Cannot load pass template")

        val runnerTemplate = this.javaClass
            .classLoader
            ?.getResourceAsStream("passManagerTest/testPassRunnerTemplate")
            ?.bufferedReader()
            ?.readText()
            ?: throw AssertionError("Cannot load pass template")

        val analysisList = nodes.map { it.requiredAnalysis.toMutableSet().apply { addAll(it.persistedAnalysis) } }
            .reduce { acc, it -> acc.apply { addAll(it) } }
            .toList()

        val allPasses = nodes.joinToString(separator = "\n\n") {
            passTemplate.format(
                "TestPass${it.name}",
                it.requiredPasses.joinToString { r -> "TestPass$r::class.java" },
                it.requiredAnalysis.joinToString { r -> "TestAnalysis$r::class.java" },
                it.persistedAnalysis.joinToString { r -> "TestAnalysis$r::class.java" }
            )
        }

        val allAnalysis = analysisList.joinToString(separator = "\n\n") {
            analysisTemplate.format("TestAnalysis$it")
        }

        val allPassesKt = "$passImportsTemplate\n$allPasses"

        val allAnalysisKt = "$passImportsTemplate\n$allAnalysis"

        val runnerKt = runnerTemplate.format(
            nodes.joinToString(separator = "\n") { "schedule<TestPass${it.name}>()" }
        )

        val compilationResult = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin("AllTestPasses.kt", allPassesKt),
                SourceFile.kotlin("AllTestAnalysis.kt", allAnalysisKt),
                SourceFile.kotlin("PassTestRunner.kt", runnerKt)
            )
            inheritClassPath = true
        }.compile()

        assertEquals(KotlinCompilation.ExitCode.OK, compilationResult.exitCode)

        val runnerClass = compilationResult.classLoader.loadClass("org.vorpal.research.kfg.TestPassRunnerTemplate")

        return runnerClass.getConstructor().newInstance() as TestPassRunnerTemplateI
    }
}

interface TestPassRunnerTemplateI {
    fun execute(pm: PassManager, provider: TestProvider, cm: ClassManager, targetMethod: List<Method>)
}

private data class DependencyNodeWrapper (
    val name: String,
    val requiredPasses: List<String>,
    val requiredAnalysis: List<String>,
    val persistedAnalysis: List<String>
) : Serializable