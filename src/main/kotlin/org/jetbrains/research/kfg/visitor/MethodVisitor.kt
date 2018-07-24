package org.jetbrains.research.kfg.visitor

import org.jetbrains.research.kfg.InvalidInstructionError
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.Parameter
import org.jetbrains.research.kfg.ir.value.instruction.*

open class MethodVisitor(val method: Method) : NodeVisitor(method) {
    override fun visit() {
        super.visit()
        method.run {
            parameters.toTypedArray().forEach { visitParameter(it) }
            basicBlocks.toTypedArray().forEach { visitBasicBlock(it) }
        }
    }

    open fun visitParameter(parameter: Parameter) {}
    open fun visitBasicBlock(bb: BasicBlock) {
        bb.instructions.toTypedArray().forEach { visitInstruction(it) }
    }

    open fun visitInstruction(inst: Instruction) {
        when (inst) {
            is ArrayLoadInst -> visitArrayLoadInst(inst)
            is ArrayStoreInst -> visitArrayStoreInst(inst)
            is BinaryInst -> visitBinaryInst(inst)
            is CallInst -> visitCallInst(inst)
            is CastInst -> visitCastInst(inst)
            is CatchInst -> visitCatchInst(inst)
            is CmpInst -> visitCmpInst(inst)
            is EnterMonitorInst -> visitEnterMonitorInst(inst)
            is ExitMonitorInst -> visitExitMonitorInst(inst)
            is FieldLoadInst -> visitFieldLoadInst(inst)
            is FieldStoreInst -> visitFieldStoreInst(inst)
            is InstanceOfInst -> visitInstanceOfInst(inst)
            is NewArrayInst -> visitNewArrayInst(inst)
            is NewInst -> visitNewInst(inst)
            is PhiInst -> visitPhiInst(inst)
            is UnaryInst -> visitUnaryInst(inst)
            is TerminateInst -> visitTerminateInst(inst)
            else -> throw InvalidInstructionError(inst)
        }
    }

    open fun visitTerminateInst(inst: TerminateInst) {
        when (inst) {
            is BranchInst -> visitBranchInst(inst)
            is JumpInst -> visitJumpInst(inst)
            is ReturnInst -> visitReturnInst(inst)
            is SwitchInst -> visitSwitchInst(inst)
            is TableSwitchInst -> visitTableSwitchInst(inst)
            is ThrowInst -> visitThrowInst(inst)
            else -> throw InvalidInstructionError(inst)
        }
    }

    open fun visitArrayLoadInst(inst: ArrayLoadInst) {}
    open fun visitArrayStoreInst(inst: ArrayStoreInst) {}
    open fun visitBinaryInst(inst: BinaryInst) {}
    open fun visitBranchInst(inst: BranchInst) {}
    open fun visitCallInst(inst: CallInst) {}
    open fun visitCastInst(inst: CastInst) {}
    open fun visitCatchInst(inst: CatchInst) {}
    open fun visitCmpInst(inst: CmpInst) {}
    open fun visitEnterMonitorInst(inst: EnterMonitorInst) {}
    open fun visitExitMonitorInst(inst: ExitMonitorInst) {}
    open fun visitFieldLoadInst(inst: FieldLoadInst) {}
    open fun visitFieldStoreInst(inst: FieldStoreInst) {}
    open fun visitInstanceOfInst(inst: InstanceOfInst) {}
    open fun visitNewArrayInst(inst: NewArrayInst) {}
    open fun visitNewInst(inst: NewInst) {}
    open fun visitPhiInst(inst: PhiInst) {}
    open fun visitUnaryInst(inst: UnaryInst) {}
    open fun visitJumpInst(inst: JumpInst) {}
    open fun visitReturnInst(inst: ReturnInst) {}
    open fun visitSwitchInst(inst: SwitchInst) {}
    open fun visitTableSwitchInst(inst: TableSwitchInst) {}
    open fun visitThrowInst(inst: ThrowInst) {}
}