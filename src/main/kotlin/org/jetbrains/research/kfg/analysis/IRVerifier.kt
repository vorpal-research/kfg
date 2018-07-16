package org.jetbrains.research.kfg.analysis

import org.jetbrains.research.kfg.ir.*
import org.jetbrains.research.kfg.ir.value.Constant
import org.jetbrains.research.kfg.ir.value.UndefinedName
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.ir.value.instruction.PhiInst
import org.jetbrains.research.kfg.ir.value.instruction.TerminateInst
import org.jetbrains.research.kfg.visitor.MethodVisitor

class IRVerifier(method: Method) : MethodVisitor(method) {
    private val valueNameRegex = Regex("(\\%([a-zA-Z][\\w\\.]*|\\d+)|arg\\$\\d+|this)")
    private val blockNameRegex = Regex("\\%[a-zA-Z][\\w\\.\$]+")
    private val valueNames = mutableMapOf<String, Value>()
    private val blockNames = mutableMapOf<String, BasicBlock>()

    private fun visitValue(value: Value) {
        if (value.name !is UndefinedName) {
            if (value is Constant) {

            } else {
                assert(valueNameRegex.matches(value.name.toString()), { "Incorrect value name format $value" })
                val storedVal = valueNames[value.name.toString()]
                assert(storedVal == null || storedVal == value, { "Same names for two different values" })
                valueNames[value.name.toString()] = value
            }
        }
    }

    override fun visitInstruction(inst: Instruction) {
        inst.operands().forEach { visitValue(it) }
        visitValue(inst)
        assert(inst.parent != null, { "Instruction $inst with no parent in method" })
        assert(method.basicBlocks.contains(inst.parent), { "Instruction parent does not belong to method" })
        super.visitInstruction(inst)
    }

    override fun visitPhiInst(inst: PhiInst) {
        val bb = inst.parent!!
        inst.getPredecessors().forEach {
            assert(method.basicBlocks.contains(it), { "Phi incoming from unknown block" })
        }
        val predecessors = when (bb) {
            is BodyBlock -> bb.predecessors
            is CatchBlock -> bb.getAllPredecessors()
            else -> setOf()
        }

        assert(predecessors.size == inst.getPredecessors().size, { "Phi insts predecessors are different from block predecessors" })
        for (pred in inst.getPredecessors()) {
            assert(predecessors.contains(pred), { "Phi insts predecessors are different from block predecessors" })
        }
    }

    override fun visitTerminateInst(inst: TerminateInst) {
        val bb = inst.parent!!
        inst.successors().forEach {
            assert(method.basicBlocks.contains(it), { "Terminate inst to unknown block" })
        }
        assert(bb.successors.size == inst.successors().size, { "Terminate insts successors are different from block successors" })
        for (succ in inst.successors()) {
            assert(bb.successors.contains(succ), { "Terminate insts successors are different from block successors" })
        }
        super.visitTerminateInst(inst)
    }

    override fun visitBasicBlock(bb: BasicBlock) {
        assert(blockNameRegex.matches(bb.name.toString()), { "Incorrect value name format ${bb.name}" })
        val storedVal = blockNames[bb.name.toString()]
        assert(storedVal == null || storedVal == bb, { "Same names for two different blocks" })
        assert(bb.parent == method, { "Block parent points to other method" })
        if (bb is CatchBlock) {
            assert(bb in method.catchEntries, { "Catch block does not belong to method catch entries" })
            assert(bb.predecessors.isEmpty(), { "Catch block should not have predecessors" })
        } else if (bb == method.getEntry()) {
            assert(bb.predecessors.isEmpty(), { "Entry block should not have predecessors" })
        } else {
            bb.predecessors.forEach {
                assert(method.basicBlocks.contains(it), { "Block predecessor does not belong to method" })
            }
        }
        bb.successors.forEach {
            assert(method.basicBlocks.contains(it), { "Block successor does not belong to method" })
        }
        assert(bb.last() is TerminateInst, { "Block should end with terminate inst" })
        assert(bb.mapNotNull { it as? TerminateInst }.size == 1, { "Block should have exactly one terminator" })
        super.visitBasicBlock(bb)
    }
}