package org.jetbrains.research.kfg.analysis

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.BodyBlock
import org.jetbrains.research.kfg.ir.CatchBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.Constant
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.ir.value.instruction.PhiInst
import org.jetbrains.research.kfg.ir.value.instruction.TerminateInst
import org.jetbrains.research.kfg.visitor.MethodVisitor

class IRVerifier(override val cm: ClassManager) : MethodVisitor {
    private val valueNameRegex = "(%([_\\-\$a-zA-Z]+[\\w.]*|\$|\\d+)|arg\\$\\d+|this)".toRegex()
    private val blockNameRegex = "%[a-zA-Z][\\w.\$]+".toRegex()
    private val valueNames = hashMapOf<String, Value>()
    private val blockNames = hashMapOf<String, BasicBlock>()

    private val Instruction.parents
        get(): Pair<Method, BasicBlock> {
            val bb = parent!!
            val method = bb.parent!!
            return method to bb
        }

    private fun visitValue(value: Value) {
        if (value.name !== UndefinedName && value !is Constant) {
            require(valueNameRegex.matches(value.name.toString())) { "Incorrect value name format $value" }
            val storedVal = valueNames[value.name.toString()]
            require(storedVal == null || storedVal == value) { "Same names for two different values" }
            valueNames[value.name.toString()] = value
        }
    }

    override fun visitInstruction(inst: Instruction) {
        val (method, _) = inst.parents

        inst.operands.forEach { visitValue(it) }
        visitValue(inst)

        inst.run {
            require(parent != null) { "Instruction $inst with no parent in method" }
            require(parent in method) { "Instruction parent does not belong to method" }
        }

        super.visitInstruction(inst)
    }

    override fun visitPhiInst(inst: PhiInst) {
        val (method, bb) = inst.parents

        for (predecessor in inst.predecessors) {
            require(predecessor in method) { "Phi ${inst.print()} incoming from unknown block" }
        }
        val predecessors = when (bb) {
            is BodyBlock -> bb.predecessors
            is CatchBlock -> bb.getAllPredecessors()
        }

        require(predecessors.size == inst.predecessors.size) {
            "Phi insts predecessors are different from block predecessors: ${inst.print()}"
        }
        for (predecessor in inst.predecessors) {
            require(predecessor in predecessors) {
                "Phi insts predecessors are different from block predecessors"
            }
        }
    }

    override fun visitTerminateInst(inst: TerminateInst) {
        val (method, bb) = inst.parents

        require(bb.successors.size == inst.successors.toSet().size) {
            "Terminate inst ${inst.print()} successors are different from block successors"
        }
        for (successor in inst.successors) {
            require(successor in method) { "Terminate inst to unknown block" }
            require(successor in bb.successors) { "Terminate insts successors are different from block successors" }
        }
        super.visitTerminateInst(inst)
    }

    override fun visitBasicBlock(bb: BasicBlock) {
        val method = bb.parent!!

        require(blockNameRegex.matches(bb.name.toString())) { "Incorrect value name format ${bb.name}" }
        val storedVal = blockNames[bb.name.toString()]
        require(storedVal == null || storedVal == bb) { "Same names for two different blocks" }
        require(bb.parent == method) { "Block parent points to other method" }
        when (bb) {
            is CatchBlock -> {
                require(bb in method.catchEntries) { "Catch block ${bb.name} does not belong to method catch entries" }
                require(bb.predecessors.isEmpty()) { "Catch block ${bb.name} should not have predecessors" }
            }
            method.entry -> require(bb.predecessors.isEmpty()) { "Entry block should not have predecessors" }
            else -> bb.predecessors.forEach {
                if (it !in method) {
                    println("error")
                }
                require(it in method) { "Block ${bb.name} predecessor ${it.name} does not belong to method" }
            }
        }
        for (successor in bb.successors) {
            require(successor in method) { "Block successor does not belong to method" }
        }
        require(bb.last() is TerminateInst) { "Block should end with terminate inst" }
        require(bb.mapNotNull { it as? TerminateInst }.size == 1) { "Block should have exactly one terminator" }
        super.visitBasicBlock(bb)
    }

    override fun cleanup() {
        valueNames.clear()
        blockNames.clear()
    }
}