package org.vorpal.research.kfg.visitor.pass.strategy

import org.vorpal.research.kfg.visitor.NodeVisitor
import org.vorpal.research.kfg.visitor.Pipeline

interface PassStrategy {
    fun isParallelSupported(): Boolean
    fun createPassOrder(pipeline: Pipeline, parallel: Boolean = false): PassOrder
}

interface PassOrder : Iterator<NodeVisitor>, Iterable<NodeVisitor> {
    override fun iterator(): Iterator<NodeVisitor> = this
}

class IteratedPassOrder(private val iterator: Iterator<NodeVisitor>) : PassOrder {
    override fun hasNext() = iterator.hasNext()
    override fun next() = iterator.next()
}