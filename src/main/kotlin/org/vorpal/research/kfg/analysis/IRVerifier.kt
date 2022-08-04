package org.vorpal.research.kfg.analysis

import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.KfgException
import org.vorpal.research.kfg.ir.*
import org.vorpal.research.kfg.ir.value.*
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.PhiInst
import org.vorpal.research.kfg.ir.value.instruction.TerminateInst
import org.vorpal.research.kfg.visitor.StandaloneMethodVisitor
import org.vorpal.research.kthelper.assert.AssertionException
import org.vorpal.research.kthelper.assert.fail
import org.vorpal.research.kthelper.assert.ktassert

class InvalidIRException(reason: Throwable) : KfgException(reason)

class IRVerifier(classManager: ClassManager) : StandaloneMethodVisitor {
    override val cm: ClassManager = classManager

    private val valueNameRegex = "(%([_\\-\$a-zA-Z]+[\\w.]*|\$|\\d+)|arg\\$\\d+|this)".toRegex()
    private val blockNameRegex = "%[a-zA-Z][\\w.\$]+".toRegex()
    private val valueNames = hashMapOf<String, Value>()
    private val blockNames = hashMapOf<String, BasicBlock>()
    private var current: MethodBody? = null
    private var usageContext: UsageContext = EmptyUsageContext

    constructor(classManager: ClassManager, ctx: UsageContext) : this(classManager) {
        this.usageContext = ctx
    }

    private fun visitValue(value: Value) = with(usageContext) {
        if (value.name !is UndefinedName && value !is Constant) {
            ktassert(valueNameRegex.matches("${value.name}"), "Incorrect value name format $value")
            val storedVal = valueNames[value.name.toString()]

            ktassert(storedVal == null || storedVal == value, "Same names for two different values")
            valueNames[value.name.toString()] = value
        }
        for (user in value.users) {
            if (user is Instruction) {
                ktassert(user.hasParent && user.parent.hasParent && user.parent.parentUnsafe == current)
            } else {
                fail("Unknown user of value $value")
            }
        }
    }

    override fun visitInstruction(inst: Instruction) {
        inst.operands.forEach { visitValue(it) }
        visitValue(inst)

        ktassert(inst.hasParent, "Instruction ${inst.print()} with no parent in method")
        ktassert(inst.parent in current!!, "Instruction ${inst.print()} parent does not belong to method")

        super.visitInstruction(inst)
    }

    override fun visitPhiInst(inst: PhiInst) {
        val bb = inst.parent

        for (predecessor in inst.predecessors) {
            ktassert(
                predecessor in current!!,
                "Phi ${inst.print()} incoming from unknown block"
            )
        }

        val predecessors = when (bb) {
            is BodyBlock -> bb.predecessors
            is CatchBlock -> bb.allPredecessors
        }

        ktassert(
            predecessors.size == inst.predecessors.size,
            "Phi instruction predecessors are different from block predecessors: ${inst.print()}"
        )

        for (predecessor in inst.predecessors) {
            ktassert(predecessor in predecessors, "Phi instruction predecessors are different from block predecessors")
        }
    }

    override fun visitTerminateInst(inst: TerminateInst) {
        val bb = inst.parent

        ktassert(
            bb.successors.size == inst.successors.toSet().size,
            "Terminate inst ${inst.print()} successors are different from block successors"
        )
        for (successor in inst.successors) {
            ktassert(successor in current!!, "Terminate inst to unknown block")
            ktassert(successor in bb.successors, "Terminate instruction successors are different from block successors")
        }
        super.visitTerminateInst(inst)
    }

    override fun visitBasicBlock(bb: BasicBlock) {
        val method = bb.parent

        ktassert(blockNameRegex.matches("${bb.name}"), "Incorrect value name format ${bb.name}")
        val storedVal = blockNames["${bb.name}"]

        ktassert(storedVal == null || storedVal == bb, "Same names for two different blocks")
        ktassert(bb.parent == method, "Block parent points to other method")

        when (bb) {
            is CatchBlock -> {
                ktassert(
                    bb in method.catchEntries,
                    "Catch block ${bb.name} does not belong to method catch entries"
                )
                ktassert(bb.predecessors.isEmpty(), "Catch block ${bb.name} should not have predecessors")
            }
            method.entry -> ktassert(bb.predecessors.isEmpty(), "Entry block should not have predecessors")
            else -> {
                for (it in bb.predecessors) {
                    ktassert(it in method, "Block ${bb.name} predecessor ${it.name} does not belong to method")
                }
            }
        }

        for (successor in bb.successors) {
            ktassert(successor in method, "Block successor does not belong to method")
        }

        ktassert(bb.last() is TerminateInst, "Block should end with terminate inst")
        ktassert(bb.mapNotNull { it as? TerminateInst }.size == 1, "Block should have exactly one terminator")
        super.visitBasicBlock(bb)
    }

    override fun visit(method: Method) {
        if (!cm.verifyIR) return
        try {
            super.visit(method)
        } catch (e: AssertionException) {
            throw InvalidIRException(e)
        } finally {
            cleanup()
        }
    }

    override fun visitBody(body: MethodBody) {
        current = body
        super.visitBody(body)
        current = null
    }

    override fun cleanup() {
        valueNames.clear()
        blockNames.clear()
    }
}