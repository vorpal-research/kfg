package org.jetbrains.research.kfg.analysis

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.util.LoopDetector
import org.jetbrains.research.kfg.visitor.MethodVisitor

class Loop(val header: BasicBlock, val body: MutableSet<BasicBlock>) : Iterable<BasicBlock> {
    var parent: Loop? = null
    val subloops = hashSetOf<Loop>()

    val method: Method?
        get() = header.parent

    val exitingBlocks: Set<BasicBlock>
        get() = body.filterNot { body.containsAll(it.successors) }.toSet()

    val loopExits: Set<BasicBlock>
        get() = body.flatMap { it.successors }.filterNot { body.contains(it) }.toSet()

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

    fun contains(bb: BasicBlock) = bb in body
    fun containsAll(blocks: Collection<BasicBlock>) = body.containsAll(blocks)

    fun addBlock(bb: BasicBlock) {
        body.add(bb)
        parent?.addBlock(bb)
    }

    fun addSubloop(loop: Loop) = subloops.add(loop)
    fun removeBlock(bb: BasicBlock) {
        body.remove(bb)
        parent?.removeBlock(bb)
    }

    override fun iterator() = body.iterator()
}


object LoopManager {
    private class LoopInfo {
        var valid = false
        val loops: List<Loop>

        constructor() {
            this.valid = false
            this.loops = listOf()
        }

        constructor(loops: List<Loop>) {
            this.valid = true
            this.loops = loops
        }
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
                val loops = LoopAnalysis(method)
                loopInfo[method] = LoopInfo(loops)
                loops
            }
        }
    }
}

object LoopAnalysis : MethodVisitor {
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

        val allLoops = LoopDetector(method.basicBlocks.toSet()).search()
                .map { Loop(it.key, it.value.toMutableSet()) }

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
            val headers = loop.body.fold(0) { acc, basicBlock ->
                if (loop.containsAll(basicBlock.predecessors)) acc else acc + 1
            }
            require(headers == 1) { "Only loops with single header are supported" }
        }
    }
}

interface LoopVisitor : MethodVisitor {
    override fun visit(method: Method) {
        val loops: List<Loop> = LoopManager.getMethodLoopInfo(method)
        loops.forEach { visit(it) }
        updateLoopInfo(method)
    }

    fun visit(loop: Loop) {
        for (it in loop.subloops) visit(it)
    }

    fun preservesLoopInfo() = false

    fun updateLoopInfo(method: Method) {
        if (!this.preservesLoopInfo()) {
            LoopManager.setInvalid(method)
        }
    }
}