package org.jetbrains.research.kfg.visitor

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.analysis.IRVerifier
import org.jetbrains.research.kfg.analysis.LoopAnalysis
import org.jetbrains.research.kfg.analysis.LoopSimplifier
import org.jetbrains.research.kfg.visitor.pass.AnalysisVisitor

typealias NodeVisitorSupplier<T> = (ClassManager, Pipeline) -> T

class VisitorRegistry {
    companion object {

        private val visitors = mutableMapOf<String, NodeVisitorSupplier<*>>()
        private val analysis = mutableMapOf<String, NodeVisitorSupplier<*>>()

        init {
            addVisitor("IRVerifier", ) { cm, _ -> IRVerifier(cm) }
            addVisitor("LoopSimplifier") { cm, _ -> LoopSimplifier(cm) }
            addVisitor("LoopAnalysis") {cm, _ -> LoopAnalysis(cm) }
        }

        fun getVisitor(name: String) = visitors[name]
        fun getAnalysis(name: String) = analysis[name]

        fun <T : NodeVisitor> addVisitor(name: String, visitorClass: Class<T>, vararg args: Any?) {
            val constructor = visitorClass.getDeclaredConstructor(
                    ClassManager::class.java,
                    Pipeline::class.java,
                    *args.map { it?.javaClass ?: Any::class.java }.toTypedArray()
            ).apply {
                isAccessible = true
            }

            addVisitor(name) { classManager, pipeline ->
                constructor.newInstance(classManager, pipeline, *args)
            }
        }

        fun <T : NodeVisitor> addVisitor(name: String, supplier: NodeVisitorSupplier<T>) {
            if (visitors.contains(name) || analysis.contains(name)) {
                throw IllegalArgumentException("Name $name is already defined")
            }

            visitors[name] = supplier
        }

        fun <T : AnalysisVisitor<*>> addAnalysis(name: String, analysisClass: Class<T>, vararg args: Any?) {
            val constructor = analysisClass.getDeclaredConstructor(
                    ClassManager::class.java,
                    Pipeline::class.java,
                    *args.map { it?.javaClass ?: Any::class.java }.toTypedArray()
            ).apply {
                isAccessible = true
            }

            addAnalysis(name) { classManager, pipeline ->
                constructor.newInstance(classManager, pipeline, *args)
            }
        }

        fun <T : AnalysisVisitor<*>> addAnalysis(name: String, supplier: NodeVisitorSupplier<T>) {
            if (visitors.contains(name) || analysis.contains(name)) {
                throw IllegalArgumentException("Name $name is already defined")
            }

            analysis[name] = supplier
        }
    }
}