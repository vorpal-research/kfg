package org.vorpal.research.kfg.visitor

import org.vorpal.research.kfg.ir.BasicBlock
import org.vorpal.research.kfg.ir.Method
import org.vorpal.research.kfg.ir.MethodBody
import org.vorpal.research.kfg.ir.value.instruction.ArrayLoadInst
import org.vorpal.research.kfg.ir.value.instruction.ArrayStoreInst
import org.vorpal.research.kfg.ir.value.instruction.BinaryInst
import org.vorpal.research.kfg.ir.value.instruction.BranchInst
import org.vorpal.research.kfg.ir.value.instruction.CallInst
import org.vorpal.research.kfg.ir.value.instruction.CastInst
import org.vorpal.research.kfg.ir.value.instruction.CatchInst
import org.vorpal.research.kfg.ir.value.instruction.CmpInst
import org.vorpal.research.kfg.ir.value.instruction.EnterMonitorInst
import org.vorpal.research.kfg.ir.value.instruction.ExitMonitorInst
import org.vorpal.research.kfg.ir.value.instruction.FieldLoadInst
import org.vorpal.research.kfg.ir.value.instruction.FieldStoreInst
import org.vorpal.research.kfg.ir.value.instruction.InstanceOfInst
import org.vorpal.research.kfg.ir.value.instruction.Instruction
import org.vorpal.research.kfg.ir.value.instruction.InvokeDynamicInst
import org.vorpal.research.kfg.ir.value.instruction.JumpInst
import org.vorpal.research.kfg.ir.value.instruction.NewArrayInst
import org.vorpal.research.kfg.ir.value.instruction.NewInst
import org.vorpal.research.kfg.ir.value.instruction.PhiInst
import org.vorpal.research.kfg.ir.value.instruction.ReturnInst
import org.vorpal.research.kfg.ir.value.instruction.SwitchInst
import org.vorpal.research.kfg.ir.value.instruction.TableSwitchInst
import org.vorpal.research.kfg.ir.value.instruction.TerminateInst
import org.vorpal.research.kfg.ir.value.instruction.ThrowInst
import org.vorpal.research.kfg.ir.value.instruction.UnaryInst
import org.vorpal.research.kfg.ir.value.instruction.UnknownValueInst
import org.vorpal.research.kfg.ir.value.instruction.UnreachableInst
import org.vorpal.research.kthelper.assert.unreachable

interface MethodVisitor : NodeVisitor {

    fun visit(method: Method) {
        super.visit(method)
        visitBody(method.body)
    }

    fun visitBody(body: MethodBody) {
        body.run {
            basicBlocks.toTypedArray().forEach { visitBasicBlock(it) }
        }
    }

    fun visitBasicBlock(bb: BasicBlock) {
        bb.instructions.toTypedArray().forEach { visitInstruction(it) }
    }

    fun visitInstruction(inst: Instruction) {
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
            is InvokeDynamicInst -> visitInvokeDynamicInst(inst)
            is NewArrayInst -> visitNewArrayInst(inst)
            is NewInst -> visitNewInst(inst)
            is PhiInst -> visitPhiInst(inst)
            is UnaryInst -> visitUnaryInst(inst)
            is TerminateInst -> visitTerminateInst(inst)
            is UnknownValueInst -> visitUnknownValueInst(inst)
            else -> unreachable("Unknown instruction ${inst.print()}")
        }
    }

    fun visitTerminateInst(inst: TerminateInst) {
        when (inst) {
            is BranchInst -> visitBranchInst(inst)
            is JumpInst -> visitJumpInst(inst)
            is ReturnInst -> visitReturnInst(inst)
            is SwitchInst -> visitSwitchInst(inst)
            is TableSwitchInst -> visitTableSwitchInst(inst)
            is ThrowInst -> visitThrowInst(inst)
            is UnreachableInst -> visitUnreachableInst(inst)
            else -> unreachable("Unknown instruction ${inst.print()}")
        }
    }

    fun visitArrayLoadInst(inst: ArrayLoadInst) {}
    fun visitArrayStoreInst(inst: ArrayStoreInst) {}
    fun visitBinaryInst(inst: BinaryInst) {}
    fun visitBranchInst(inst: BranchInst) {}
    fun visitCallInst(inst: CallInst) {}
    fun visitCastInst(inst: CastInst) {}
    fun visitCatchInst(inst: CatchInst) {}
    fun visitCmpInst(inst: CmpInst) {}
    fun visitEnterMonitorInst(inst: EnterMonitorInst) {}
    fun visitExitMonitorInst(inst: ExitMonitorInst) {}
    fun visitFieldLoadInst(inst: FieldLoadInst) {}
    fun visitFieldStoreInst(inst: FieldStoreInst) {}
    fun visitInstanceOfInst(inst: InstanceOfInst) {}
    fun visitInvokeDynamicInst(inst: InvokeDynamicInst) {}
    fun visitNewArrayInst(inst: NewArrayInst) {}
    fun visitNewInst(inst: NewInst) {}
    fun visitPhiInst(inst: PhiInst) {}
    fun visitUnaryInst(inst: UnaryInst) {}
    fun visitJumpInst(inst: JumpInst) {}
    fun visitReturnInst(inst: ReturnInst) {}
    fun visitSwitchInst(inst: SwitchInst) {}
    fun visitTableSwitchInst(inst: TableSwitchInst) {}
    fun visitThrowInst(inst: ThrowInst) {}
    fun visitUnreachableInst(inst: UnreachableInst) {}
    fun visitUnknownValueInst(inst : UnknownValueInst) {}
}
