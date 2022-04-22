package org.jetbrains.research.kfg.pm.passes

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.visitor.*
import org.jetbrains.research.kfg.visitor.pass.AnalysisVisitor

class P1(override val cm: ClassManager, override val pipeline: Pipeline) : TestPass() {
    override fun passDependencies(): List<Class<out MethodVisitor>> = listOf()
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A1::class.java, A2::class.java)
    override fun analysisDependenciesPersisted(): List<Class<out AnalysisVisitor<*>>> = listOf(A1::class.java, A2::class.java, A11::class.java)
}

class P2(override val cm: ClassManager, override val pipeline: Pipeline) : TestPass() {
    override fun passDependencies(): List<Class<out MethodVisitor>> = listOf()
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A1::class.java, A2::class.java)
    override fun analysisDependenciesPersisted(): List<Class<out AnalysisVisitor<*>>> = listOf()
}

class P3(override val cm: ClassManager, override val pipeline: Pipeline) : TestPass() {
    override fun passDependencies(): List<Class<out MethodVisitor>> = listOf()
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A3::class.java)
    override fun analysisDependenciesPersisted(): List<Class<out AnalysisVisitor<*>>> = listOf(A1::class.java, A6::class.java, A7::class.java, A10::class.java)
}

class P4(override val cm: ClassManager, override val pipeline: Pipeline) : TestPass() {
    override fun passDependencies(): List<Class<out MethodVisitor>> = listOf()
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf()
    override fun analysisDependenciesPersisted(): List<Class<out AnalysisVisitor<*>>> = listOf(A2::class.java, A6::class.java, A8::class.java, A9::class.java)
}

class P5(override val cm: ClassManager, override val pipeline: Pipeline) : TestPass() {
    override fun passDependencies(): List<Class<out MethodVisitor>> = listOf()
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A1::class.java, A4::class.java, A5::class.java)
    override fun analysisDependenciesPersisted(): List<Class<out AnalysisVisitor<*>>> = listOf(A1::class.java)
}

class P6(override val cm: ClassManager, override val pipeline: Pipeline) : TestPass() {
    override fun passDependencies(): List<Class<out MethodVisitor>> = listOf(P1::class.java, P3::class.java)
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A1::class.java, A3::class.java, A6::class.java)
    override fun analysisDependenciesPersisted(): List<Class<out AnalysisVisitor<*>>> = listOf(A1::class.java, A4::class.java, A9::class.java, A10::class.java, A12::class.java)
}

class P7(override val cm: ClassManager, override val pipeline: Pipeline) : TestPass() {
    override fun passDependencies(): List<Class<out MethodVisitor>> = listOf(P4::class.java)
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A2::class.java, A7::class.java, A8::class.java)
    override fun analysisDependenciesPersisted(): List<Class<out AnalysisVisitor<*>>> = listOf(A2::class.java, A3::class.java, A8::class.java, A10::class.java, A11::class.java)
}

class P8(override val cm: ClassManager, override val pipeline: Pipeline) : TestPass() {
    override fun passDependencies(): List<Class<out MethodVisitor>> = listOf(P5::class.java)
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A1::class.java, A9::class.java, A10::class.java)
    override fun analysisDependenciesPersisted(): List<Class<out AnalysisVisitor<*>>> = listOf()
}

class P9(override val cm: ClassManager, override val pipeline: Pipeline) : TestPass() {
    override fun passDependencies(): List<Class<out MethodVisitor>> = listOf(P6::class.java)
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A4::class.java, A7::class.java, A11::class.java, A16::class.java)
    override fun analysisDependenciesPersisted(): List<Class<out AnalysisVisitor<*>>> = listOf(A1::class.java, A2::class.java, A8::class.java, A11::class.java, A13::class.java)
}

class P10(override val cm: ClassManager, override val pipeline: Pipeline) : TestPass() {
    override fun passDependencies(): List<Class<out MethodVisitor>> = listOf(P5::class.java, P7::class.java)
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A3::class.java, A2::class.java, A12::class.java)
    override fun analysisDependenciesPersisted(): List<Class<out AnalysisVisitor<*>>> = listOf(A4::class.java, A6::class.java, A9::class.java, A13::class.java, A14::class.java)
}

class P11(override val cm: ClassManager, override val pipeline: Pipeline) : TestPass() {
    override fun passDependencies(): List<Class<out MethodVisitor>> = listOf(P6::class.java)
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A3::class.java, A7::class.java, A10::class.java,  A13::class.java)
    override fun analysisDependenciesPersisted(): List<Class<out AnalysisVisitor<*>>> = listOf(A1::class.java, A3::class.java, A5::class.java, A6::class.java, A9::class.java)
}

class P12(override val cm: ClassManager, override val pipeline: Pipeline) : TestPass() {
    override fun passDependencies(): List<Class<out MethodVisitor>> = listOf(P6::class.java, P7::class.java)
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A11::class.java, A12::class.java, A13::class.java)
    override fun analysisDependenciesPersisted(): List<Class<out AnalysisVisitor<*>>> = listOf()
}

class P13(override val cm: ClassManager, override val pipeline: Pipeline) : TestPass() {
    override fun passDependencies(): List<Class<out MethodVisitor>> = listOf(P1::class.java, P6::class.java, P9::class.java)
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A1::class.java, A7::class.java, A6::class.java, A11::class.java)
    override fun analysisDependenciesPersisted(): List<Class<out AnalysisVisitor<*>>> = listOf(A12::class.java, A7::class.java, A11::class.java, A14::class.java, A15::class.java)
}

class P14(override val cm: ClassManager, override val pipeline: Pipeline) : TestPass() {
    override fun passDependencies(): List<Class<out MethodVisitor>> = listOf(P3::class.java, P11::class.java)
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A14::class.java, A15::class.java)
    override fun analysisDependenciesPersisted(): List<Class<out AnalysisVisitor<*>>> = listOf(A12::class.java)
}

class P15(override val cm: ClassManager, override val pipeline: Pipeline) : TestPass() {
    override fun passDependencies(): List<Class<out MethodVisitor>> = listOf(P13::class.java)
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A12::class.java, A14::class.java, A16::class.java)
    override fun analysisDependenciesPersisted(): List<Class<out AnalysisVisitor<*>>> = listOf(A2::class.java, A6::class.java, A12::class.java)
}

class P16(override val cm: ClassManager, override val pipeline: Pipeline) : TestPass() {
    override fun passDependencies(): List<Class<out MethodVisitor>> = listOf(P15::class.java)
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A2::class.java, A13::class.java, A15::class.java)
    override fun analysisDependenciesPersisted(): List<Class<out AnalysisVisitor<*>>> = listOf(A2::class.java, A13::class.java, A15::class.java)
}

class P10Circular(override val cm: ClassManager, override val pipeline: Pipeline) : TestPass() {
    override fun passDependencies(): List<Class<out MethodVisitor>> = listOf(P5::class.java, P7Circular::class.java)
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A3::class.java, A2::class.java, A12::class.java)
    override fun analysisDependenciesPersisted(): List<Class<out AnalysisVisitor<*>>> = listOf(A4::class.java, A6::class.java, A9::class.java, A13::class.java, A14::class.java)
}

class P7Circular(override val cm: ClassManager, override val pipeline: Pipeline) : TestPass() {
    override fun passDependencies(): List<Class<out MethodVisitor>> = listOf(P4::class.java, P10Circular::class.java)
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A2::class.java, A7::class.java, A8::class.java)
    override fun analysisDependenciesPersisted(): List<Class<out AnalysisVisitor<*>>> = listOf(A2::class.java, A3::class.java, A8::class.java, A10::class.java, A11::class.java)
}

class P3CircularAnalysis(override val cm: ClassManager, override val pipeline: Pipeline) : TestPass() {
    override fun passDependencies(): List<Class<out MethodVisitor>> = listOf()
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A3::class.java, A7Circular::class.java)
    override fun analysisDependenciesPersisted(): List<Class<out AnalysisVisitor<*>>> = listOf(A1::class.java, A6::class.java, A7Circular::class.java, A10::class.java)
}