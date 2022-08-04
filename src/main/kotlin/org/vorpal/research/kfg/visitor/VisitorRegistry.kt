package org.vorpal.research.kfg.visitor

import org.vorpal.research.kfg.visitor.pass.AnalysisResult
import org.vorpal.research.kfg.visitor.pass.AnalysisVisitor
import org.vorpal.research.kfg.visitor.pass.IllegalPipelineException

private typealias VisitorClass = Class<out NodeVisitor>
private typealias ProviderClass = Class<out KfgProvider>
private typealias AnalysisClass = Class<out AnalysisVisitor<out AnalysisResult>>

class VisitorRegistry {

    private val visitorDependencies = mutableMapOf<VisitorClass, MutableSet<VisitorClass>>()
    private val visitorSoftDependencies = mutableMapOf<VisitorClass, MutableSet<VisitorClass>>()
    private val providerDependencies = mutableMapOf<VisitorClass, MutableSet<ProviderClass>>()
    private val analysisDependencies = mutableMapOf<VisitorClass, MutableSet<AnalysisClass>>()
    private val analysisPersistedResults = mutableMapOf<VisitorClass, MutableSet<AnalysisClass>>()
    private val analysisPersistedAll = mutableSetOf<VisitorClass>()
    private val providers = mutableMapOf<ProviderClass, KfgProvider>()

    val visitorsCount get() = visitorDependencies.size
    val analysisCount get() = analysisDependencies.size

    internal fun getVisitorDependencies(nodeVisitor: VisitorClass) : Set<VisitorClass> = visitorDependencies[nodeVisitor] ?: emptySet()
    internal fun getVisitorSoftDependencies(nodeVisitor: VisitorClass) : Set<VisitorClass> = visitorSoftDependencies[nodeVisitor] ?: emptySet()
    internal fun getProviderDependencies(nodeVisitor: VisitorClass) : Set<ProviderClass> = providerDependencies[nodeVisitor] ?: emptySet()
    internal fun getAnalysisDependencies(nodeVisitor: VisitorClass): Set<AnalysisClass> = analysisDependencies[nodeVisitor] ?: emptySet()
    internal fun getAnalysisPersisted(nodeVisitor: VisitorClass): Set<AnalysisClass> = analysisPersistedResults[nodeVisitor] ?: emptySet()

    internal fun getRegisteredAnalysis() = analysisDependencies.values.flatten().distinct()

    fun addRequiredPass(visitor: VisitorClass, dependency: VisitorClass) {
        visitorDependencies.computeIfAbsent(visitor) { mutableSetOf() }.add(dependency)
    }

    fun addSoftDependencyPass(visitor: VisitorClass, dependency: VisitorClass) {
        visitorSoftDependencies.computeIfAbsent(visitor) { mutableSetOf() }.add(dependency)
    }

    fun addRequiresProvider(visitor: VisitorClass, dependency: ProviderClass) {
        providerDependencies.computeIfAbsent(visitor) { mutableSetOf() }.add(dependency)
    }

    fun addRequiredAnalysis(visitor: VisitorClass, dependency: AnalysisClass) {
        analysisDependencies.computeIfAbsent(visitor) { mutableSetOf() }.add(dependency)
    }

    fun addPersistedAnalysis(visitor: VisitorClass, dependency: AnalysisClass) {
        analysisPersistedResults.computeIfAbsent(visitor) { mutableSetOf() }.add(dependency)
    }

    fun getProvider(provider: ProviderClass): KfgProvider = try {
        providers[provider]!!
    } catch (e: NullPointerException) {
        throw IllegalPipelineException("Required provider ${provider.name} but it is not registered. Try registering provider before scheduling a pass")
    }

    fun getProviderNullable(provider: ProviderClass): KfgProvider? = providers[provider]

    fun registerProvider(provider: KfgProvider) {
        providers[provider::class.java] = provider
    }
}