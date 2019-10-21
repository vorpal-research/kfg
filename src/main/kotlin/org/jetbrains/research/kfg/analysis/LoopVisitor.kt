package org.jetbrains.research.kfg.analysis

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.util.Graph
import org.jetbrains.research.kfg.util.GraphNode
import org.jetbrains.research.kfg.util.LoopDetector
import org.jetbrains.research.kfg.visitor.MethodVisitor

data class LoopNode(val parent: Loop, val block: BasicBlock) : GraphNode<LoopNode> {
    override val predecessors: Set<LoopNode>
        get() = block.predecessors.filter { it in parent.body }.map { LoopNode(parent, it) }.toSet()

    override val successors: Set<LoopNode>
        get() = block.successors.filter { it in parent.body }.map { LoopNode(parent, it) }.toSet()
}

class Loop(val header: BasicBlock, val body: MutableSet<BasicBlock>) : Graph<LoopNode>, Iterable<LoopNode> {
    var parent: Loop? = null
    val subloops = hashSetOf<Loop>()

    override val entry: LoopNode
        get() = LoopNode(this, header)

    override val nodes: Set<LoopNode>
        get() = body.map { LoopNode(this, it) }.toSet()

    val method: Method?
        get() = header.parent

    val exitingBlocks: Set<BasicBlock>
        get() = body.filterNot { body.containsAll(it.successors) }.toSet()

    val loopExits: Set<BasicBlock>
        get() = body.flatMap { it.successors }.asSequence().filterNot { body.contains(it) }.toSet()

    val preheaders: List<BasicBlock>
        get() = header.predecessors.filter { !body.contains(it) }

    val preheader: BasicBlock
        get() = preheaders.first()

    val latches: Set<BasicBlock>
        get() = body.filter { it.successors.contains(header) }.toSet()

    val latch: BasicBlock
        get() = latches.first()

    val hasSinglePreheader get() = preheaders.size == 1
    val hasSingleLatch get() = body.filter { it.successors.contains(header) }.toSet().size == 1

    fun containsAll(blocks: Collection<LoopNode>) = body.containsAll(blocks.map { it.block })

    fun addBlock(bb: BasicBlock) {
        body.add(bb)
        parent?.addBlock(bb)
    }

    fun addSubloop(loop: Loop) = subloops.add(loop)
    fun removeBlock(bb: BasicBlock) {
        body.remove(bb)
        parent?.removeBlock(bb)
    }

    override fun iterator() = nodes.iterator()

    operator fun contains(block: BasicBlock) = block in body
}


object LoopManager {
    private class LoopInfo(val loops: List<Loop>, var valid: Boolean) {
        constructor() : this(listOf(), false)
        constructor(loops: List<Loop>) : this(loops, true)
    }

    private val loopInfo = mutableMapOf<Method, LoopInfo>()

    fun setInvalid(method: Method) {
        loopInfo.getOrPut(method, LoopManager::LoopInfo).valid = false
    }

    fun getMethodLoopInfo(method: Method): List<Loop> {
        val info = loopInfo.getOrPut(method, LoopManager::LoopInfo)
        return when {
            info.valid -> info.loops
            else -> {
                val loops = performLoopAnalysis(method)
                loopInfo[method] = LoopInfo(loops)
                loops
            }
        }
    }
}

fun performLoopAnalysis(method: Method): List<Loop> {
    val la = LoopAnalysis(method.cm)
    return la.invoke(method)
}

class LoopAnalysis(override val cm: ClassManager) : MethodVisitor {
    private val loops = arrayListOf<Loop>()

    override fun cleanup() {
        loops.clear()
    }

    operator fun invoke(method: Method): List<Loop> {
        visit(method)
        return loops.toList()
    }

    override fun visit(method: Method) {
        cleanup()

        val allLoops = LoopDetector(method).search().map { Loop(it.key, it.value.toMutableSet()) }

        val parents = hashMapOf<Loop, MutableSet<Loop>>()
        for (loop in allLoops) {
            for (parent in allLoops) {
                val set = parents.getOrPut(loop, ::hashSetOf)
                if (loop != parent && loop.header in parent)
                    set.add(parent)
            }
        }
        loops.addAll(parents.filter { it.value.isEmpty() }.keys)

        var numLoops = loops.size
        while (numLoops < allLoops.size) {
            val remove = hashSetOf<Loop>()
            val removableParents = hashSetOf<Loop>()

            for ((child, possibleParents) in parents) {
                if (possibleParents.size == 1) {
                    possibleParents.first().addSubloop(child)
                    child.parent = possibleParents.first()

                    remove.add(child)
                    removableParents.add(possibleParents.first())
                    ++numLoops
                }
            }
            remove.forEach { parents.remove(it) }
            for (it in removableParents) {
                for ((_, possibleParents) in parents) {
                    possibleParents.remove(it)
                }
            }
        }

        for (loop in allLoops) {
            val headers = loop.body.count { !it.predecessors.all { pred -> pred in loop } }
            require(headers == 1) { "Only loops with single header are supported" }
        }
    }
}

interface LoopVisitor : MethodVisitor {
    val preservesLoopInfo get() = false

    override fun visit(method: Method) {
        val loops = LoopManager.getMethodLoopInfo(method)
        loops.forEach { visit(it) }
        updateLoopInfo(method)
    }

    fun visit(loop: Loop) {
        for (it in loop.subloops) visit(it)
    }

    fun updateLoopInfo(method: Method) {
        if (!this.preservesLoopInfo) {
            LoopManager.setInvalid(method)
        }
    }
}