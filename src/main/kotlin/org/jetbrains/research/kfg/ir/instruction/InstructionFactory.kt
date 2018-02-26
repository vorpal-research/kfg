package org.jetbrains.research.kfg.ir.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.value.Value


class InstructionFactory private constructor() {
    private object Holder {
        val instance = InstructionFactory()
    }

    companion object {
        val instance: InstructionFactory by lazy { Holder.instance }
    }

    fun getAssign(lhv: Value, rhv: Value): Instruction = AssignInst(lhv, rhv)
    fun getStore(array: Value, index: Value, value: Value): Instruction = StoreInst(array, index, value)
    fun getReturn(): Instruction = ReturnInst()
    fun getReturn(retval: Value): Instruction = ReturnInst(retval)
    fun getEnterMonitor(owner: Value): Instruction = EnterMonitorInst(owner)
    fun getExitMonitor(owner: Value): Instruction = ExitMonitorInst(owner)
    fun getJump(successor: BasicBlock): Instruction = JumpInst(successor)
    fun getBranch(cond: Value, trueSucc: BasicBlock, falseSucc: BasicBlock): Instruction = BranchInst(cond, trueSucc, falseSucc)
    fun getSwitch(key: Value, default: BasicBlock, branches: Map<Value, BasicBlock>): Instruction = SwitchInst(key, default, branches)
    fun getTableSwitch(index: Value, min: Value, max: Value, default: BasicBlock, branches: Array<BasicBlock>): Instruction =
            TableSwitchInst(index, min, max, default, branches)
    fun getThrow(throwable: Value): Instruction = ThrowInst(throwable)
    fun getCall(callExpr: Value): Instruction = CallInst(callExpr)
    fun getCall(lhv: Value, callExpr: Value): Instruction = CallInst(lhv, callExpr)
}