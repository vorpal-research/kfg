package org.vorpal.research.kfg.pm.passes

import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.visitor.Pipeline
import org.vorpal.research.kfg.visitor.pass.AnalysisVisitor

/*
 *  Analysis | Dependencies
 *  1..3   | -
 *  4      | 1, 3
 *  5      | 1, 4
 *  6      | 4
 *  7..9   | 2, 4..6
 *  10     | 1, 7
 *  11     | 4, 6, 8
 *  12     | 2, 11
 *  13..16 | (it - 1)
 */


class A1(override val cm: ClassManager, override val pipeline: Pipeline) : TestAnalysis() {
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf()
}

class A2(override val cm: ClassManager, override val pipeline: Pipeline) : TestAnalysis() {
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf()
}

class A3(override val cm: ClassManager, override val pipeline: Pipeline) : TestAnalysis() {
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf()
}

class A4(override val cm: ClassManager, override val pipeline: Pipeline) : TestAnalysis() {
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A1::class.java, A3::class.java)
}

class A5(override val cm: ClassManager, override val pipeline: Pipeline) : TestAnalysis() {
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A1::class.java, A4::class.java)
}

class A6(override val cm: ClassManager, override val pipeline: Pipeline) : TestAnalysis() {
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A4::class.java)
}

class A7(override val cm: ClassManager, override val pipeline: Pipeline) : TestAnalysis() {
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A2::class.java, A4::class.java, A5::class.java, A6::class.java)
}

class A8(override val cm: ClassManager, override val pipeline: Pipeline) : TestAnalysis() {
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A2::class.java, A4::class.java, A5::class.java, A6::class.java)
}

class A9(override val cm: ClassManager, override val pipeline: Pipeline) : TestAnalysis() {
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A2::class.java, A4::class.java, A5::class.java, A6::class.java)
}

class A10(override val cm: ClassManager, override val pipeline: Pipeline) : TestAnalysis() {
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A1::class.java, A7::class.java)
}

class A11(override val cm: ClassManager, override val pipeline: Pipeline) : TestAnalysis() {
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A4::class.java, A6::class.java, A8::class.java)
}

class A12(override val cm: ClassManager, override val pipeline: Pipeline) : TestAnalysis() {
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A2::class.java, A11::class.java)
}

class A13(override val cm: ClassManager, override val pipeline: Pipeline) : TestAnalysis() {
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A12::class.java)
}

class A14(override val cm: ClassManager, override val pipeline: Pipeline) : TestAnalysis() {
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A13::class.java)
}

class A15(override val cm: ClassManager, override val pipeline: Pipeline) : TestAnalysis() {
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A14::class.java)
}

class A16(override val cm: ClassManager, override val pipeline: Pipeline) : TestAnalysis() {
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A15::class.java)
}

class A7Circular(override val cm: ClassManager, override val pipeline: Pipeline) : TestAnalysis() {
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A2::class.java, A4::class.java, A5::class.java, A6::class.java, A10Circular::class.java)
}

class A10Circular(override val cm: ClassManager, override val pipeline: Pipeline) : TestAnalysis() {
    override fun analysisDependencies(): List<Class<out AnalysisVisitor<*>>> = listOf(A1::class.java, A7Circular::class.java)
}