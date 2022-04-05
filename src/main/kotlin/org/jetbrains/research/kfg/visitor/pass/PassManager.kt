package org.jetbrains.research.kfg.visitor.pass

import org.jetbrains.research.kfg.visitor.NodeVisitor
import org.jetbrains.research.kfg.visitor.Pipeline
import org.jetbrains.research.kfg.visitor.pass.strategy.PassStrategy
import org.jetbrains.research.kfg.visitor.pass.strategy.topologic.DefaultPassStrategy

class PassManager(private val passStrategy: PassStrategy = DefaultPassStrategy()) {
    fun getPassOrder(pipeline: Pipeline, parallel: Boolean = false) =
            passStrategy.createPassOrder(pipeline, parallel)

    fun verify(pipeline: Pipeline) {
        verifyDependencyInstances(pipeline)
        verifyCircularDependencies(pipeline)
    }

    private fun verifyDependencyInstances(pipeline: Pipeline) {
        val registry = pipeline.visitorRegistry
        val passes = pipeline.getPasses().toSet()
        val passesAsClass = passes.map { it::class.java }.toSet()

        fun exception(missed: Class<*>, forPass: Class<out NodeVisitor>) {
            throw IllegalPipelineException("${missed.name} is missed for pass ${forPass.name}")
        }

        for (pass in passes) {
            registry.getVisitorDependencies(pass::class.java).forEach {
                if (!passesAsClass.contains(it)) exception(it, pass.javaClass)
            }
            registry.getProviderDependencies(pass::class.java).forEach {
                if (registry.getProviderNullable(it) == null) exception(it, pass.javaClass)
            }
        }
    }

    private fun verifyCircularDependencies(pipeline: Pipeline) {
        val registry = pipeline.visitorRegistry
        val passes = pipeline.getPasses().toSet()
        val passesAsClass = passes.map { it::class.java }.toSet()
        val analysisAsClass = registry.getRegisteredAnalysis()

        val checkedPasses = mutableSetOf<Class<out NodeVisitor>>()

        fun exception(pass: Class<out NodeVisitor>, dependant: Class<out NodeVisitor>) {
            throw IllegalPipelineException("Circular dependency: ${dependant.name} and ${pass.name}")
        }

        fun verifyPass(pass: Class<out NodeVisitor>, dependants: MutableSet<Class<out NodeVisitor>>) {
            if (!checkedPasses.add(pass)) {
                return
            }

            val dependentsOn = registry.getVisitorDependencies(pass)

            for (p in dependentsOn) {
                if (dependants.contains(p)) exception(pass, p)
            }

            for (p in dependentsOn) {
                dependants.add(p)
                verifyPass(p, dependants)
                dependants.remove(p)
            }
        }

        for (pass in passesAsClass) {
            verifyPass(pass, mutableSetOf())
        }

        for (analysis in analysisAsClass) {
            verifyPass(analysis, mutableSetOf())
        }
    }
}