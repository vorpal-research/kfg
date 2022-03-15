package org.jetbrains.research.kfg.analysis

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.KfgException
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.BodyBlock
import org.jetbrains.research.kfg.ir.CatchBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.ir.value.instruction.Instruction
import org.jetbrains.research.kfg.ir.value.instruction.PhiInst
import org.jetbrains.research.kfg.ir.value.instruction.TerminateInst
import org.jetbrains.research.kfg.visitor.MethodVisitor
import org.jetbrains.research.kthelper.assert.AssertionException
import org.jetbrains.research.kthelper.assert.fail
import org.jetbrains.research.kthelper.assert.ktassert

class InvalidIRException(reason: Throwable) : KfgException(reason)

class IRVerifier(classManager: ClassManager) : MethodVisitor {
    override val cm: ClassManager = classManager
    private val valueNameRegex = "(%([_\\-\$a-zA-Z]+[\\w.]*|\$|\\d+)|arg\\$\\d+|this)".toRegex()
    private val blockNameRegex = "%[a-zA-Z][\\w.\$]+".toRegex()
    private val valueNames = hashMapOf<String, Value>()
    private val blockNames = hashMapOf<String, BasicBlock>()
    private var current: Method? = null
    private var usageContext: UsageContext = EmptyUsageContext

    constructor(classManager: ClassManager, ctx: UsageContext) : this(classManager) {
        this.usageContext = ctx
    }

    private val Instruction.parents
        get(): Pair<Method, BasicBlock> {
            val bb = parent
            val method = bb.parent
            return method to bb
        }

    private fun visitValue(value: Value) = with (usageContext) {
        if (value.name !is UndefinedName && value !is Constant) {
            ktassert(valueNameRegex.matches("${value.name}"), "Incorrect value name format $value")
            val storedVal = valueNames[value.name.toString()]

            ktassert(storedVal == null || storedVal == value, "Same names for two different values")
            valueNames[value.name.toString()] = value
        }
        for (user in value.users) {
            if (user is Instruction) {
                ktassert(user.hasParent && user.parent.hasParent && user.parent.parent == current)
            } else {
                fail("Unknown user of value $value")
            }
        }
    }

    override fun visitInstruction(inst: Instruction) {
        val (method, _) = inst.parents

        inst.operands.forEach { visitValue(it) }
        visitValue(inst)

        ktassert(inst.hasParent, "Instruction ${inst.print()} with no parent in method")
        ktassert(inst.parent in method, "Instruction ${inst.print()} parent does not belong to method")

        super.visitInstruction(inst)
    }

    override fun visitPhiInst(inst: PhiInst) {
        val (method, bb) = inst.parents

        for (predecessor in inst.predecessors) {
            ktassert(predecessor in method) {
                "Phi ${inst.print()} incoming from unknown block"
            }
        }

        val predecessors = when (bb) {
            is BodyBlock -> bb.predecessors
            is CatchBlock -> bb.allPredecessors
        }

        if (inst.predecessors.size != predecessors.size) {
            val a = 10
        }
        ktassert(predecessors.size == inst.predecessors.size) {
            "Phi instruction predecessors are different from block predecessors: ${inst.print()}"
        }

        for (predecessor in inst.predecessors) {
            ktassert(predecessor in predecessors, "Phi instruction predecessors are different from block predecessors")
        }
    }

    override fun visitTerminateInst(inst: TerminateInst) {
        val (method, bb) = inst.parents

        ktassert(bb.successors.size == inst.successors.toSet().size) {
            "Terminate inst ${inst.print()} successors are different from block successors"
        }
        for (successor in inst.successors) {
            ktassert(successor in method, "Terminate inst to unknown block")
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
                ktassert(bb in method.catchEntries, "Catch block ${bb.name} does not belong to method catch entries")
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
            current = method
            super.visit(method)
            current = null
        } catch (e: AssertionException) {
            throw InvalidIRException(e)
        } finally {
            cleanup()
        }
    }

    override fun cleanup() {
        valueNames.clear()
        blockNames.clear()
    }
}