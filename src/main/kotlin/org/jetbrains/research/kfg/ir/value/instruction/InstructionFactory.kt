package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.ValueName
import org.jetbrains.research.kfg.type.Type

class InstructionFactory private constructor() {
    private object Holder {
        val instance = InstructionFactory()
    }

    companion object {
        val instance: InstructionFactory by lazy { Holder.instance }
    }

    fun getNewArray(name: ValueName, componentType: Type, count: Value): Instruction = NewArrayInst(name, componentType, count)
    fun getMultiNewArray(name: ValueName, type: Type, dims: Int): Instruction = MultiNewArray(name, type, dims)
    fun getArrayLoad(name: ValueName, arrayRef: Value, index: Value): Instruction = ArrayLoadInst(name, arrayRef, index)
    fun getArrayStore(array: Value, index: Value, value: Value): Instruction = ArrayStoreInst(array, index, value)

    fun getFieldLoad(name: ValueName, field: Value): Instruction = FieldLoadInst(name, field)
    fun getFieldStore(field: Value, value: Value): Instruction = FieldStoreInst(field, value)

    fun getBinary(name: ValueName, opcode: BinaryOpcode, lhv: Value, rhv: Value): Instruction = BinaryInst(name, opcode, lhv, rhv)
    fun getCmp(name: ValueName, opcode: CmpOpcode, lhv: Value, rhv: Value): Instruction = CmpInst(name, opcode, lhv, rhv)
    fun getCast(name: ValueName, type: Type, obj: Value): Instruction = CastInst(name, type, obj)
    fun getInstanceOf(name: ValueName, targetType: Type, obj: Value): Instruction = InstanceOfInst(name, targetType, obj)
    fun getNew(name: ValueName, type: Type): Instruction = NewInst(name, type)
    fun getUnary(name: ValueName, opcode: UnaryOpcode, obj: Value): Instruction = UnaryInst(name, opcode, obj)

    fun getEnterMonitor(owner: Value): Instruction = EnterMonitorInst(owner)
    fun getExitMonitor(owner: Value): Instruction = ExitMonitorInst(owner)

    fun getJump(successor: BasicBlock): Instruction = JumpInst(successor)
    fun getBranch(cond: Value, trueSucc: BasicBlock, falseSucc: BasicBlock): Instruction = BranchInst(cond, trueSucc, falseSucc)
    fun getSwitch(key: Value, default: BasicBlock, branches: Map<Value, BasicBlock>): Instruction = SwitchInst(key, default, branches)
    fun getTableSwitch(index: Value, min: Value, max: Value, default: BasicBlock, branches: Array<BasicBlock>): Instruction =
            TableSwitchInst(index, min, max, default, branches)

    fun getPhi(name: ValueName, type: Type, incomings: Map<BasicBlock, Value>): Instruction = PhiInst(name, type, incomings)

    fun getCall(method: Method, `class`: Class, args: Array<Value>): Instruction = CallInst(method, `class`, args)
    fun getCall(method: Method, `class`: Class, obj: Value, args: Array<Value>): Instruction = CallInst(method, `class`, obj, args)
    fun getCall(name: ValueName, method: Method, `class`: Class, args: Array<Value>): Instruction = CallInst(name, method, `class`, args)
    fun getCall(name: ValueName, method: Method, `class`: Class, obj: Value, args: Array<Value>): Instruction = CallInst(name, method, `class`, obj, args)

    fun getCatch(name: ValueName, type: Type): Instruction = CatchInst(name, type)
    fun getThrow(throwable: Value): Instruction = ThrowInst(throwable)

    fun getReturn(): Instruction = ReturnInst()
    fun getReturn(retval: Value): Instruction = ReturnInst(retval)
}