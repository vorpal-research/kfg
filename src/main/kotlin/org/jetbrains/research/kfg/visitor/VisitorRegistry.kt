package org.jetbrains.research.kfg.visitor

import org.jetbrains.research.kfg.visitor.pass.AnalysisResult
import org.jetbrains.research.kfg.visitor.pass.AnalysisVisitor

class VisitorRegistry {

    private val visitorDependencies = mutableMapOf<Class<out NodeVisitor>, MutableSet<Class<out NodeVisitor>>>()
    private val visitorSoftDependencies = mutableMapOf<Class<out NodeVisitor>, MutableSet<Class<out NodeVisitor>>>()
    private val providerDependencies = mutableMapOf<Class<out NodeVisitor>, MutableSet<Class<out KfgProvider<*>>>>()
    private val analysisDependencies = mutableMapOf<Class<out NodeVisitor>, MutableSet<Class<out AnalysisVisitor<out AnalysisResult>>>>()
    private val analysisPersistedResults = mutableMapOf<Class<out NodeVisitor>, MutableSet<Class<out AnalysisVisitor<out AnalysisResult>>>>()
    private val analysisPersistedAll = mutableSetOf<Class<out NodeVisitor>>()
    private val providers = mutableMapOf<Class<out KfgProvider<*>>, KfgProvider<*>>()

    fun getVisitorDependencies(nodeVisitor: Class<out NodeVisitor>) : Set<Class<out NodeVisitor>> = visitorDependencies[nodeVisitor] ?: emptySet()
    fun getVisitorSoftDependencies(nodeVisitor: Class<out NodeVisitor>) : Set<Class<out NodeVisitor>> = visitorSoftDependencies[nodeVisitor] ?: emptySet()
    fun getProviderDependencies(nodeVisitor: Class<out NodeVisitor>) : Set<Class<out KfgProvider<*>>> = providerDependencies[nodeVisitor] ?: emptySet()
    fun getAnalysisDependencies(nodeVisitor: Class<out NodeVisitor>): Set<Class<out AnalysisVisitor<out AnalysisResult>>> = analysisDependencies[nodeVisitor] ?: emptySet()
    fun getAnalysisPersisted(nodeVisitor: Class<out NodeVisitor>): Set<Class<out AnalysisVisitor<out AnalysisResult>>> = analysisPersistedResults[nodeVisitor] ?: emptySet()

    fun getVisitorsCount() = visitorDependencies.size
    fun getAnalysisCount() = analysisDependencies.size

    fun addRequiredPass(visitor: Class<out NodeVisitor>, dependency: Class<out NodeVisitor>) {
        visitorDependencies.computeIfAbsent(visitor) { mutableSetOf() }.add(dependency)
    }

    fun addSoftDependencyPass(visitor: Class<out NodeVisitor>, dependency: Class<out NodeVisitor>) {
        visitorSoftDependencies.computeIfAbsent(visitor) { mutableSetOf() }.add(dependency)
    }

    fun addRequiresProvider(visitor: Class<out NodeVisitor>, dependency: Class<out KfgProvider<*>>) {
        providerDependencies.computeIfAbsent(visitor) { mutableSetOf() }.add(dependency)
    }

    fun addRequiredAnalysis(visitor: Class<out NodeVisitor>, dependency: Class<out AnalysisVisitor<out AnalysisResult>>) {
        analysisDependencies.computeIfAbsent(visitor) { mutableSetOf() }.add(dependency)
    }

    fun addPersistedAnalysis(visitor: Class<out NodeVisitor>, dependency: Class<out AnalysisVisitor<out AnalysisResult>>) {
        analysisPersistedResults.computeIfAbsent(visitor) { mutableSetOf() }.add(dependency)
    }

    fun getProviderNullable(provider: Class<out KfgProvider<*>>): KfgProvider<*>? = providers[provider]

    fun getProvider(provider: Class<out KfgProvider<*>>): KfgProvider<*> = providers[provider]!!

    fun registerProvider(provider: KfgProvider<*>) {
        providers[provider::class.java] = provider
    }

    internal fun getRegisteredAnalysis() = analysisDependencies.keys.toSet()
}