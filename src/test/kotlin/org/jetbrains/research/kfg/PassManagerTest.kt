package org.jetbrains.research.kfg

import org.jetbrains.research.kfg.container.JarContainer
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.Node
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kfg.visitor.Pipeline
import org.jetbrains.research.kfg.visitor.VisitorRegistry
import org.jetbrains.research.kfg.visitor.executePipeline
import org.jetbrains.research.kfg.visitor.pass.AnalysisManager
import org.jetbrains.research.kfg.visitor.pass.AnalysisResult
import org.jetbrains.research.kfg.visitor.pass.AnalysisVisitor
import org.jetbrains.research.kfg.visitor.pass.PassManager
import org.jetbrains.research.kfg.visitor.pass.strategy.astar.AStarPassStrategy
import org.jetbrains.research.kfg.visitor.pass.strategy.dynamic.DynamicPassStrategy
import org.jetbrains.research.kfg.visitor.pass.strategy.iterativeastar.IterativeAStarPassStrategy
import org.jetbrains.research.kfg.visitor.pass.strategy.topologic.DefaultPassStrategy
import org.junit.*
import java.io.*
import kotlin.random.Random

class PassManagerTest {

    private val PASSES_COUNT = 100
    private val ANALYSIS_COUNT = 100
    private val ROOT_CHANCE = 0.2f
    private val CONNECTEDNESS = 1f
    private val DATASET_COUNT = 15

    private val out = System.out
    private val err = System.err

    private val passStrategiesToTest = listOf(
        DefaultPassStrategy(),
        IterativeAStarPassStrategy(),
        AStarPassStrategy(),
        DynamicPassStrategy(),
    )

    val pkg = Package.parse("org.jetbrains.research.kfg.*")
    lateinit var jar: JarContainer
    lateinit var cm: ClassManager

    @Before
    fun setUp() {
        System.setOut(PrintStream(out))
        System.setErr(PrintStream(err))

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

    @After
    fun tearDown() {
        System.setOut(System.out)
        System.setErr(System.err)
    }

    @Test
    //@Ignore
    fun generateTestData() {
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
            repeat(rng.nextInt((analysis.size * CONNECTEDNESS * 0.75f).toInt())) { indexRequired ->
                requiredAnalysis.add(analysis[indexRequired])
            }

            analysis.shuffle()
            val persistedAnalysis = mutableListOf<String>()
            repeat(rng.nextInt((analysis.size * CONNECTEDNESS * 1f).toInt())) { indexPersisted ->
                persistedAnalysis.add(analysis[indexPersisted])
            }

            nodes.add(DependencyNodeWrapper((index + 1).toString(), requiredPasses, requiredAnalysis, persistedAnalysis))
        }

        val oos = ObjectOutputStream(FileOutputStream(File("dataset_${DATASET_COUNT}")))
        oos.writeObject(nodes)
    }

    @Test
    fun testPipeline() {
        val klass = run {
            var temp = cm.concreteClasses.random()
            while (temp.methods.isEmpty())
                temp = cm.concreteClasses.random()
            temp
        }
        val targetMethod = klass.getMethods(klass.methods.random().name).toList()[0]
        val targetMethods = listOf(targetMethod)

        for (datasetId in 1..DATASET_COUNT) {
            val dataset = readDataset(datasetId)
            var count = 0
            var countAnalysis = 0

            val passedSet = mutableSetOf<String>()

            VisitorRegistry.clearAll()
            generateVisitors(dataset,
                { wrapper, classManager, pipeline, node ->
                    //println("Pass ${wrapper.name}")

                    wrapper.requiredAnalysis.forEach { analysisName ->
                        pipeline.analysisManager.getAnalysisResult<AnalysisResultDummy>(analysisName, node)
                    }

                    pipeline.analysisManager.invalidateAllExcept(wrapper.persistedAnalysis, node)

                    count += 1

                    wrapper.requiredPasses.forEach { requiredPass ->
                        if (!passedSet.contains(requiredPass)) {
                            Assert.fail()
                        }
                    }

                    passedSet.add(wrapper.name)
                },
                { name, classManager, pipeline, node ->
                    // println("Analysis ${wrapper.name}")

                    countAnalysis += 1
                }
            )

            for (passStrategy in passStrategiesToTest) {
                executePipeline(cm, targetMethods) {
                    dataset.forEach { add(it.name) }
                    passManager = PassManager(passStrategy)
                }
                println("Dataset ${datasetId}, PassStrategy - ${passStrategy.javaClass.name.substringAfterLast('.')}")
                println("Pass count $count")
                println("Analysis count $countAnalysis")
                count = 0
                countAnalysis = 0
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readDataset(index: Int): List<DependencyNodeWrapper> {
        val ois = ObjectInputStream(FileInputStream(File("dataset_${index}")))
        return ois.readObject() as List<DependencyNodeWrapper>
    }

    private fun generateVisitors(
            nodes: List<DependencyNodeWrapper>,
            visitAction: (DependencyNodeWrapper, ClassManager, Pipeline, Node) -> Unit,
            analysisAction: (String, ClassManager, Pipeline, Node) -> Unit
    ) {
        val passCount = nodes.maxOf { it.name.toInt() }
        val analysisCount = nodes.maxOf { it.requiredAnalysis.maxOfOrNull { analysis -> analysis.toInt() } ?: 0 }
        nodes.forEach {
            VisitorRegistry.addVisitor(it.name) { cm, pipeline ->
                object : MethodVisitor {
                    override val cm: ClassManager
                        get() = cm
                    override val pipeline: Pipeline
                        get() = pipeline

                    override fun getName(): String = it.name
                    override fun cleanup() { }
                    override fun visit(method: Method) {
                        visitAction.invoke(it, cm, pipeline, method)
                    }
                    override fun getRequiredPasses(): List<String> = it.requiredPasses
                    override fun getRequiredAnalysisVisitors(): List<String> = it.requiredAnalysis
                    override fun getPersistedAnalysisVisitors(): List<String> = it.persistedAnalysis

                }
            }
        }

        for (index in passCount + 1 .. analysisCount) {
            VisitorRegistry.addAnalysis(index.toString()) { cm, pipeline ->
                object : AnalysisVisitor<AnalysisResultDummy> {
                    override val cm: ClassManager
                        get() = cm
                    override val pipeline: Pipeline
                        get() = pipeline

                    override fun getName(): String = index.toString()
                    override fun analyse(node: Node): AnalysisResultDummy {
                        analysisAction(index.toString(), cm, pipeline, node)
                        return AnalysisResultDummy(index.toString())
                    }
                }
            }
        }
    }
}

data class DependencyNodeWrapper (
        val name: String,
        val requiredPasses: List<String>,
        val requiredAnalysis: List<String>,
        val persistedAnalysis: List<String>
) : Serializable

class AnalysisResultDummy(val analysisVisitor: String) : AnalysisResult
