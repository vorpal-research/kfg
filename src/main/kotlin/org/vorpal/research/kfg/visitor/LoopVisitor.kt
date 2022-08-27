package org.vorpal.research.kfg.visitor

import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.CatchBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.Node
import org.vorpal.research.kfg.visitor.pass.AnalysisResult
import org.vorpal.research.kfg.visitor.pass.AnalysisVisitor
import org.vorpal.research.kthelper.assert.asserted
import org.vorpal.research.kthelper.graph.GraphView
import org.vorpal.research.kthelper.graph.LoopDetector
import org.vorpal.research.kthelper.graph.PredecessorGraph
import org.vorpal.research.kthelper.graph.Viewable

interface LoopVisitor : MethodVisitor {
    val preservesLoopInfo get() = false

    override fun visit(method: Method): Unit = try {
        val loops = cm.loopManager.getMethodLoopInfo(method)
        loops.forEach { visitLoop(it) }
    } finally {
        updateLoopInfo(method)
    }

    fun visitLoop(loop: Loop) {
        for (it in loop.subLoops) visitLoop(it)
    }

    fun updateLoopInfo(method: Method) {
        if (!this.preservesLoopInfo) {
            cm.loopManager.setInvalid(method)
        }
    }
}

data class LoopNode(val parent: Loop, val block: BasicBlock) : PredecessorGraph.PredecessorVertex<LoopNode> {
    override val predecessors: Set<LoopNode>
        get() = block.predecessors.filter { it in parent.body }.map { LoopNode(parent, it) }.toSet()

    override val successors: Set<LoopNode>
        get() = block.successors.filter { it in parent.body }.map { LoopNode(parent, it) }.toSet()
}

class Loop(val header: BasicBlock, val body: MutableSet<BasicBlock>) : PredecessorGraph<LoopNode>, Iterable<LoopNode>, Viewable {
    internal var parentUnsafe: Loop? = null

    val parent get() = asserted(hasParent) { parentUnsafe!! }
    val hasParent get() = parentUnsafe != null

    val subLoops = hashSetOf<Loop>()

    override val entry: LoopNode
        get() = LoopNode(this, header)

    override val nodes: Set<LoopNode>
        get() = body.map { LoopNode(this, it) }.toSet()

    val method: Method?
        get() = header.methodUnsafe

    val allEntries: Set<BasicBlock>
        get() = body.filterNot {
            when (it) {
                is CatchBlock -> body.containsAll(it.allPredecessors)
                else -> body.containsAll(it.predecessors)
            }
        }.toSet()

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
        parentUnsafe?.addBlock(bb)
    }

    fun addSubLoop(loop: Loop) = subLoops.add(loop)
    fun removeBlock(bb: BasicBlock) {
        body.remove(bb)
        parentUnsafe?.removeBlock(bb)
    }

    override fun iterator() = nodes.iterator()

    operator fun contains(block: BasicBlock) = block in body

    override val graphView: List<GraphView>
        get() {
            val views = hashMapOf<String, GraphView>()

            nodes.forEach { node ->
                views[node.block.name.toString()] = GraphView(node.block.name.toString(), "${node.block.name}\\l")
            }

            nodes.forEach {
                val current = views.getValue(it.block.name.toString())
                for (successor in it.successors) {
                    current.addSuccessor(views.getValue(successor.block.name.toString()))
                }
            }

            return views.values.toList()
        }
}

fun performLoopAnalysis(method: Method): List<Loop> {
    val pipeline = executePipeline(method.cm, listOf(method)) {
        schedule<LoopAnalysis>()
    }
    return pipeline.getAnalysis<LoopAnalysisResult>(method).loops
}

class LoopAnalysis(override val cm: ClassManager, override val pipeline: Pipeline) : AnalysisVisitor<LoopAnalysisResult> {
    override fun analyse(node: Node): LoopAnalysisResult {
        require(node is Method) { "LoopAnalysis can only work on methods"}

        val loops = arrayListOf<Loop>()

        val allLoops = LoopDetector(node.body).search().map { Loop(it.key, it.value.toMutableSet()) }

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
                    possibleParents.first().addSubLoop(child)
                    child.parentUnsafe = possibleParents.first()

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

        return LoopAnalysisResult(loops.toList())
    }
}

data class LoopAnalysisResult(val loops: List<Loop>) : AnalysisResult