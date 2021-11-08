package org.jetbrains.research.kfg.visitor.pass

import org.jetbrains.research.kfg.visitor.NodeVisitor
import org.jetbrains.research.kfg.visitor.Pipeline

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