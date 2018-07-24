package org.jetbrains.research.kfg.analysis

import org.jetbrains.research.kfg.IF
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.BodyBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.instruction.PhiInst
import org.jetbrains.research.kfg.ir.value.instruction.TerminateInst
import org.jetbrains.research.kfg.util.LoopDetector
import org.jetbrains.research.kfg.visitor.LoopVisitor
import org.jetbrains.research.kfg.visitor.MethodVisitor

class Loop(val header: BasicBlock, val body: MutableSet<BasicBlock>) {
    var parent: Loop? = null
    val subloops = hashSetOf<Loop>()

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

    fun hasSinglePreheader() = preheaders.size == 1
    fun hasSingleLatch() = body.filter { it.successors.contains(header) }.toSet().size == 1
    fun contains(bb: BasicBlock) = body.contains(bb)
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
        loopInfo.getOrPut(method, ::LoopInfo).valid = false
    }

    fun getMethodLoopInfo(method: Method): List<Loop> {
        val info = loopInfo.getOrPut(method, ::LoopInfo)
        return when {
            info.valid -> info.loops
            else -> {
                val la = LoopAnalysis(method)
                la.visit()
                loopInfo[method] = LoopInfo(la.loops)
                la.loops
            }
        }
    }
}

class LoopAnalysis(method: Method) : MethodVisitor(method) {
    val loops = arrayListOf<Loop>()

    override fun visit() {
        loops.clear()

        val allLoops = LoopDetector(method.basicBlocks.toSet()).search()
                .map { Loop(it.key, it.value.toMutableSet()) }

        val parents = hashMapOf<Loop, MutableSet<Loop>>()
        for (loop in allLoops) {
            for (parent in allLoops) {
                val set = parents.getOrPut(loop, ::hashSetOf)
                if (loop != parent && parent.contains(loop.header))
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

class LoopSimplifier(method: Method) : LoopVisitor(method) {
    override fun preservesLoopInfo() = false

    override fun visit() {
        super.visit()
        IRVerifier(method).visit()
    }

    override fun visitLoop(loop: Loop) {
        super.visitLoop(loop)
        buildPreheader(loop)
        buildLatch(loop)
    }

    private fun remapBlocks(target: BasicBlock, from: BasicBlock, to: BasicBlock) {
        target.removeSuccessor(from)
        from.removePredecessor(target)
        target.addSuccessor(to)
        to.addPredecessor(target)
        target.terminator.replaceUsesOf(from, to)
    }

    private fun remapPhis(target: BasicBlock, from: Set<BasicBlock>, to: BasicBlock) {
        target.instructions.mapNotNull { it as? PhiInst }.forEach {
            val fromIncomings = it.incomings.filter { it.key in from }
            val fromValues = fromIncomings.values.toSet()
            val toValue = when {
                fromValues.size == 1 -> fromValues.first()
                else -> {
                    val phi = IF.getPhi(it.type, fromIncomings)
                    to.addInstruction(phi)
                    phi
                }
            }

            val targetIncomings = it.incomings.filter { it.key !in from }.toMutableMap()
            targetIncomings[to] = toValue
            val targetPhi = IF.getPhi(it.type, targetIncomings)
            target.insertBefore(it, targetPhi)
            it.replaceAllUsesWith(targetPhi)
            target.remove(it)
        }
    }

    private fun buildPreheader(loop: Loop) {
        val header = loop.header
        val loopPredecessors = header.predecessors.filter { !loop.contains(it) }.toSet()
        if (loopPredecessors.size == 1) return

        val preheader = BodyBlock("loop.preheader")
        loopPredecessors.forEach { remapBlocks(it, header, preheader) }
        preheader.addSuccessor(header)
        header.addPredecessor(preheader)

        remapPhis(header, loopPredecessors, preheader)
        preheader.addInstruction(IF.getJump(header))
        method.addBefore(header, preheader)
    }

    private fun buildLatch(loop: Loop) {
        val header = loop.header
        val latches = loop.body.filter { it.successors.contains(header) }.toSet()
        if (latches.size == 1) return

        val latch = BodyBlock("loop.latch")
        latches.forEach { remapBlocks(it, header, latch) }
        latch.addSuccessor(header)
        header.addPredecessor(latch)

        remapPhis(header, latches, latch)
        latch.addInstruction(IF.getJump(header))
        method.add(latch)
        loop.addBlock(latch)
    }
}