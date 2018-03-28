package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.StrName
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.ValueName
import org.jetbrains.research.kfg.type.Type

object InstructionFactory {
    fun getNewArray(name: String, componentType: Type, count: Value): Instruction = getNewArray(StrName(name), componentType, count)
    fun getNewArray(name: ValueName, componentType: Type, count: Value): Instruction = NewArrayInst(name, componentType, count)

    fun getMultiNewArray(name: String, type: Type, dims: Int): Instruction = getMultiNewArray(StrName(name), type, dims)
    fun getMultiNewArray(name: ValueName, type: Type, dims: Int): Instruction = MultiNewArrayInst(name, type, dims)

    fun getArrayLoad(name: String, arrayRef: Value, index: Value): Instruction = getArrayLoad(StrName(name), arrayRef, index)
    fun getArrayLoad(name: ValueName, arrayRef: Value, index: Value): Instruction = ArrayLoadInst(name, arrayRef, index)

    fun getArrayStore(array: Value, index: Value, value: Value): Instruction = ArrayStoreInst(array, index, value)


    fun getFieldLoad(name: String, field: Field): Instruction = getFieldLoad(StrName(name), field)
    fun getFieldLoad(name: ValueName, field: Field): Instruction = FieldLoadInst(name, field)

    fun getFieldLoad(name: String, owner: Value, field: Field): Instruction = getFieldLoad(StrName(name), owner, field)
    fun getFieldLoad(name: ValueName, owner: Value, field: Field): Instruction = FieldLoadInst(name, owner, field)

    fun getFieldStore(field: Field, value: Value): Instruction = FieldStoreInst(field, value)
    fun getFieldStore(owner: Value, field: Field, value: Value): Instruction = FieldStoreInst(owner, field, value)


    fun getBinary(name: String, opcode: BinaryOpcode, lhv: Value, rhv: Value): Instruction = getBinary(StrName(name), opcode, lhv, rhv)
    fun getBinary(name: ValueName, opcode: BinaryOpcode, lhv: Value, rhv: Value): Instruction = BinaryInst(name, opcode, lhv, rhv)

    fun getCmp(name: String, opcode: CmpOpcode, lhv: Value, rhv: Value): Instruction = getCmp(StrName(name), opcode, lhv, rhv)
    fun getCmp(name: ValueName, opcode: CmpOpcode, lhv: Value, rhv: Value): Instruction = CmpInst(name, opcode, lhv, rhv)

    fun getCast(name: String, type: Type, obj: Value): Instruction = getCast(StrName(name), type, obj)
    fun getCast(name: ValueName, type: Type, obj: Value): Instruction = CastInst(name, type, obj)

    fun getInstanceOf(name: String, targetType: Type, obj: Value): Instruction = getInstanceOf(StrName(name), targetType, obj)
    fun getInstanceOf(name: ValueName, targetType: Type, obj: Value): Instruction = InstanceOfInst(name, targetType, obj)

    fun getNew(name: String, type: Type): Instruction = getNew(StrName(name), type)
    fun getNew(name: String, `class`: Class): Instruction = getNew(StrName(name), `class`)
    fun getNew(name: ValueName, `class`: Class): Instruction = getNew(name, TF.getRefType(`class`))
    fun getNew(name: ValueName, type: Type): Instruction = NewInst(name, type)

    fun getUnary(name: String, opcode: UnaryOpcode, obj: Value): Instruction = getUnary(StrName(name), opcode, obj)
    fun getUnary(name: ValueName, opcode: UnaryOpcode, obj: Value): Instruction = UnaryInst(name, opcode, obj)


    fun getEnterMonitor(owner: Value): Instruction = EnterMonitorInst(owner)
    fun getExitMonitor(owner: Value): Instruction = ExitMonitorInst(owner)

    fun getJump(successor: BasicBlock): Instruction = JumpInst(successor)
    fun getBranch(cond: Value, trueSucc: BasicBlock, falseSucc: BasicBlock): Instruction = BranchInst(cond, trueSucc, falseSucc)
    fun getSwitch(key: Value, default: BasicBlock, branches: Map<Value, BasicBlock>): Instruction = SwitchInst(key, default, branches)
    fun getTableSwitch(index: Value, min: Value, max: Value, default: BasicBlock, branches: Array<BasicBlock>): Instruction =
            TableSwitchInst(index, min, max, default, branches)

    fun getPhi(name: String, type: Type, incomings: Map<BasicBlock, Value>): Instruction = getPhi(StrName(name), type, incomings)
    fun getPhi(name: ValueName, type: Type, incomings: Map<BasicBlock, Value>): Instruction = PhiInst(name, type, incomings)

    fun getCall(opcode: CallOpcode, method: Method, `class`: Class, args: Array<Value>): Instruction
            = CallInst(opcode, method, `class`, args)
    fun getCall(opcode: CallOpcode, method: Method, `class`: Class, obj: Value, args: Array<Value>): Instruction
            = CallInst(opcode, method, `class`, obj, args)

    fun getCall(opcode: CallOpcode, name: String, method: Method, `class`: Class, args: Array<Value>): Instruction
            = getCall(opcode, StrName(name), method, `class`, args)
    fun getCall(opcode: CallOpcode, name: ValueName, method: Method, `class`: Class, args: Array<Value>): Instruction
            = CallInst(opcode, name, method, `class`, args)

    fun getCall(opcode: CallOpcode, name: String, method: Method, `class`: Class, obj: Value, args: Array<Value>): Instruction
            = getCall(opcode, StrName(name), method, `class`, obj, args)
    fun getCall(opcode: CallOpcode, name: ValueName, method: Method, `class`: Class, obj: Value, args: Array<Value>): Instruction
            = CallInst(opcode, name, method, `class`, obj, args)

    fun getCatch(name: String, type: Type): Instruction = getCatch(StrName(name), type)
    fun getCatch(name: ValueName, type: Type): Instruction = CatchInst(name, type)
    fun getThrow(throwable: Value): Instruction = ThrowInst(throwable)

    fun getReturn(): Instruction = ReturnInst()
    fun getReturn(retval: Value): Instruction = ReturnInst(retval)
}