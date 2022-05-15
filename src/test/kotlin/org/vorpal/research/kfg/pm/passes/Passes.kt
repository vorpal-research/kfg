package org.vorpal.research.kfg.pm.passes

import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.Node
import org.vorpal.research.kfg.visitor.*
import org.vorpal.research.kfg.visitor.pass.AnalysisResult
import org.vorpal.research.kfg.visitor.pass.AnalysisVisitor

/*
 *  Passes count: 16
 *  Analysis count: 16
 *
 *  Dependency Levels:
 *    L1   L2   L3   L4   L5   L6
 *  |  1 |  6 |  9 | 13 | 15 | 16 |
 *  |  2 |  7 | 10 | 14 |    |    |
 *  |  3 |  8 | 11 |    |    |    |
 *  |  4 |    | 12 |    |    |    |
 *  |  5 |    |    |    |    |    |
 *
 *  Passes Dependencies:
 *  | - | 1, 3 | 6    | 1, 6, 9     | 13 | 15 |
 *  | - | 4    | 5, 7 | 3, 11       |    |    |
 *  | - | 5    | 6    |             |    |    |
 *  | - |      | 6, 7 |             |    |    |
 *  | - |      |      |             |    |    |
 *
 *  Passes Analysis Dependencies:
 *  | 1, 2    | 1, 3, 6  | 4, 7, 11, 16 | 1, 7, 6, 11 | 12, 14, 16 | 2, 13, 15 |
 *  | 1, 2    | 2, 7, 8  | 3, 2, 12     | 14, 15      |            |           |
 *  | 3       | 1, 9, 10 | 3, 7, 10, 13 |             |            |           |
 *  | -       |          | 11, 12, 13   |             |            |           |
 *  | 1, 4, 5 |          |              |             |            |           |
 *
 *  Passes Persisted Analysis:
 *  | 1, 2, 11    | 1, 4, 9, 10, 12 | 1, 2, 8, 11, 13 | 12, 7, 11, 14, 15 | 2, 6, 12 | 2, 13, 15 |
 *  | -           | 2, 3, 8, 10, 11 | 4, 6, 9, 13, 14 | 12                |          |           |
 *  | 1, 6, 7, 10 | -               | 1, 3, 5, 6, 9   |                   |          |           |
 *  | 2  6, 8, 9  |                 | -               |                   |          |           |
 *  | 1           |                 |                 |                   |          |           |
 *  
 *  Passes to call:
 *  2, 8, 10, 12, 14, 16
 */

data class TestPipelineContext(
    val executedPasses: MutableList<Class<out MethodVisitor>> = mutableListOf(),
    val executedAnalysis: MutableList<Class<out AnalysisVisitor<*>>> = mutableListOf()
)

class TestProvider : KfgProvider<TestPipelineContext> {
    private val context = TestPipelineContext()

    override fun provide(): TestPipelineContext {
        return context
    }
}

class TestAnalysisResult : AnalysisResult

abstract class TestPass : MethodVisitor {
    override fun cleanup() {}

    override fun visit(method: Method) {
        val context = getProvider<TestProvider, TestPipelineContext>().provide()

        if (context.executedPasses.contains(this.javaClass)) {
            throw IllegalStateException("Pass ${javaClass.name} executed second time.")
        }

        for (clazz in passDependencies()) {
            if (!context.executedPasses.contains(clazz)) {
                throw IllegalStateException("Cannot execute pass ${javaClass.name} while dependencies not resolved.")
            }
        }

        for (clazz in analysisDependencies()) {
            this.pipeline.analysisManager.getAnalysisResult<TestAnalysisResult>(clazz, method)
        }

        invalidateAnalysisCache(method)
        context.executedPasses.add(this.javaClass)
    }

    override fun registerPassDependencies() {
        for (clazz in passDependencies()) {
            this.pipeline.visitorRegistry.addRequiredPass(this::class.java, clazz)
        }
        addRequiredProvider<TestProvider>()
    }

    override fun registerAnalysisDependencies() {
        for (clazz in analysisDependencies()) {
            this.pipeline.visitorRegistry.addRequiredAnalysis(this::class.java, clazz)
        }
        for (clazz in analysisDependenciesPersisted()) {
            this.pipeline.visitorRegistry.addPersistedAnalysis(this::class.java, clazz)
        }
    }

    abstract fun passDependencies(): List<Class<out MethodVisitor>>
    abstract fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>>
    abstract fun analysisDependenciesPersisted(): List<Class<out AnalysisVisitor<*>>>
}

abstract class TestAnalysis : AnalysisVisitor<TestAnalysisResult> {
    override fun analyse(node: Node): TestAnalysisResult {
        getProvider<TestProvider, TestPipelineContext>()
            .provide()
            .executedAnalysis
            .add(this.javaClass)

        return TestAnalysisResult()
    }

    override fun registerAnalysisDependencies() {
        for (clazz in analysisDependencies()) {
            this.pipeline.visitorRegistry.addRequiredAnalysis(this::class.java, clazz)
        }
    }

    abstract fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>>
}

