package org.vorpal.research.kfg.visitor.pass.strategy

import org.vorpal.research.kfg.visitor.NodeVisitor
import org.vorpal.research.kfg.visitor.Pipeline

interface PassStrategy {
    fun isParallelSupported(): Boolean
    fun createPassOrder(pipeline: Pipeline, parallel: Boolean = false): PassOrder
}

interface PassOrder: Iterable<NodeVisitor> {
    override fun iterator(): Iterator<NodeVisitor> {
        return object : Iterator<NodeVisitor> {
            override fun hasNext(): Boolean = with(this@PassOrder) { hasNext() }

            override fun next(): NodeVisitor = with(this@PassOrder) { getNext() }
        }
    }

    fun hasNext(): Boolean
    fun getNext(): NodeVisitor

    fun isParallelSupported(): Boolean
    fun getNextParallel(): NodeVisitor
    fun completedParallel(pass: NodeVisitor)
}

class IteratedPassOrder(private val iterator: Iterator<NodeVisitor>) : PassOrder {
    override fun hasNext() = iterator.hasNext()

    override fun getNext() = iterator.next()

    override fun isParallelSupported() = false

    override fun getNextParallel(): NodeVisitor = throw NotImplementedError("Parallel execution is not supported for this pass order")

    override fun completedParallel(pass: NodeVisitor) = throw NotImplementedError("Parallel execution is not supported for this pass order")
}