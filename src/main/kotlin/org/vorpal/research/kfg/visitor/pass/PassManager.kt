package org.vorpal.research.kfg.visitor.pass

import org.vorpal.research.kfg.visitor.NodeVisitor
import org.vorpal.research.kfg.visitor.Pipeline
import org.vorpal.research.kfg.visitor.pass.strategy.PassOrder
import org.vorpal.research.kfg.visitor.pass.strategy.PassStrategy
import org.vorpal.research.kfg.visitor.pass.strategy.topologic.DefaultPassStrategy

class PassManager(private val passStrategy: PassStrategy = DefaultPassStrategy()) {
    fun getPassOrder(pipeline: Pipeline): PassOrder {
        // Add existing soft dependencies to real one
        val registry = pipeline.internalVisitorRegistry
        val passes = pipeline.passes
        val passesAsClass = passes.map { it::class.java }.toSet()
        for (pass in passes) {
            registry.getVisitorSoftDependencies(pass.javaClass)
                .filter { passesAsClass.contains(it) }
                .forEach { registry.addRequiredPass(pass.javaClass, it) }
        }

        return passStrategy.createPassOrder(pipeline)
    }

    fun verify(pipeline: Pipeline) {
        verifyDependencyInstances(pipeline)
        verifyCircularDependencies(pipeline)
    }

    private fun verifyDependencyInstances(pipeline: Pipeline) {
        val registry = pipeline.internalVisitorRegistry
        val passes = pipeline.passes.toSet()
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
        val registry = pipeline.internalVisitorRegistry
        val passes = pipeline.passes.toSet()
        val passesAsClass = passes.map { it::class.java }.toSet()
        val analysisAsClass = registry.getRegisteredAnalysis()

        val checkedPasses = mutableSetOf<Class<out NodeVisitor>>()

        fun exception(pass: Class<out NodeVisitor>, dependant: Class<out NodeVisitor>) {
            throw IllegalPipelineException("Circular dependency: ${dependant.name} and ${pass.name}")
        }

        fun verifyPass(
            pass: Class<out NodeVisitor>,
            dependants: MutableSet<Class<out NodeVisitor>>,
            getDependsOn: (p: Class<out NodeVisitor>) -> Set<Class<out NodeVisitor>>
        ) {
            if (!checkedPasses.add(pass)) {
                return
            }

            val dependsOn = getDependsOn(pass)

            for (p in dependsOn) {
                if (dependants.contains(p)) exception(pass, p)
            }

            dependants.add(pass)
            for (p in dependsOn) {
                verifyPass(p, dependants, getDependsOn)
            }
            dependants.remove(pass)
        }

        for (pass in passesAsClass) {
            verifyPass(pass, mutableSetOf()) {
                registry.getVisitorDependencies(it)
                    .toMutableSet()
                    .apply { addAll(registry.getVisitorSoftDependencies(it)) }
            }
        }

        for (analysis in analysisAsClass) {
            verifyPass(analysis, mutableSetOf()) {
                registry.getAnalysisDependencies(it)
            }
        }
    }
}