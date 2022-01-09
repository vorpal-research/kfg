package org.jetbrains.research.kfg.visitor.pass

import org.jetbrains.research.kfg.visitor.Pipeline
import org.jetbrains.research.kfg.visitor.pass.strategy.PassStrategy
import org.jetbrains.research.kfg.visitor.pass.strategy.topologic.DefaultPassStrategy

class PassManager(private val passStrategy: PassStrategy = DefaultPassStrategy()) {
    fun getPassOrder(pipeline: Pipeline, parallel: Boolean = false) =
            passStrategy.createPassOrder(pipeline, parallel)
}