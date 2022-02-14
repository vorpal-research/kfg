package org.jetbrains.research.kfg.visitor

import org.jetbrains.research.kfg.visitor.pass.AnalysisResult
import org.jetbrains.research.kfg.visitor.pass.AnalysisVisitor

class VisitorRegistry {

    private val visitorDependencies = mutableMapOf<Class<out NodeVisitor>, MutableSet<Class<out NodeVisitor>>>()
    private val analysisDependencies = mutableMapOf<Class<out NodeVisitor>, MutableSet<Class<out AnalysisVisitor<out AnalysisResult>>>>()
    private val analysisPersistedResults = mutableMapOf<Class<out NodeVisitor>, MutableSet<Class<out AnalysisVisitor<out AnalysisResult>>>>()
    private val providers = mutableMapOf<Class<out KfgProvider<*>>, KfgProvider<*>>()

    fun getVisitorDependencies(nodeVisitor: Class<out NodeVisitor>) : Set<Class<out NodeVisitor>> = visitorDependencies[nodeVisitor] ?: emptySet()
    fun getAnalysisDependencies(nodeVisitor: Class<out NodeVisitor>): Set<Class<out AnalysisVisitor<out AnalysisResult>>> = analysisDependencies[nodeVisitor] ?: emptySet()
    fun getAnalysisPersisted(nodeVisitor: Class<out NodeVisitor>): Set<Class<out AnalysisVisitor<out AnalysisResult>>> = analysisPersistedResults[nodeVisitor] ?: emptySet()

    fun getVisitorsCount() = visitorDependencies.size
    fun getAnalysisCount() = analysisDependencies.size

    fun getProvider(provider: Class<out KfgProvider<*>>): KfgProvider<*> {
        return providers[provider]!!
    }

    fun addRequiresPass(visitor: Class<out NodeVisitor>, dependency: Class<out NodeVisitor>) {
        visitorDependencies.computeIfAbsent(visitor) { mutableSetOf() }.add(dependency)
    }

    fun addRequiresAnalysis(visitor: Class<out NodeVisitor>, dependency: Class<out AnalysisVisitor<out AnalysisResult>>) {
        analysisDependencies.computeIfAbsent(visitor) { mutableSetOf() }.add(dependency)
    }

    fun addInvalidatesAnalysis(visitor: Class<out NodeVisitor>, dependency: Class<out AnalysisVisitor<out AnalysisResult>>) {
        analysisPersistedResults.computeIfAbsent(visitor) { mutableSetOf() }.add(dependency)
    }

    fun registerProvider(provider: KfgProvider<*>) {
        providers[provider::class.java] = provider
    }
}