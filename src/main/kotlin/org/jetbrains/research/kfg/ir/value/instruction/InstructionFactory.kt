package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.StringName
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.Name
import org.jetbrains.research.kfg.ir.value.Slot
import org.jetbrains.research.kfg.type.Type

object InstructionFactory {
    fun getNewArray(name: String, componentType: Type, count: Value): Instruction = getNewArray(StringName(name), componentType, count)
    fun getNewArray(name: Name, componentType: Type, count: Value): Instruction = NewArrayInst(name, componentType, count)
    fun getNewArray(componentType: Type, count: Value): Instruction = NewArrayInst(Slot(), componentType, count)

    fun getMultiNewArray(name: String, type: Type, dims: Int): Instruction = getMultiNewArray(StringName(name), type, dims)
    fun getMultiNewArray(name: Name, type: Type, dims: Int): Instruction = MultiNewArrayInst(name, type, dims)
    fun getMultiNewArray(type: Type, dims: Int): Instruction = MultiNewArrayInst(Slot(), type, dims)

    fun getArrayLoad(name: String, arrayRef: Value, index: Value): Instruction = getArrayLoad(StringName(name), arrayRef, index)
    fun getArrayLoad(name: Name, arrayRef: Value, index: Value): Instruction = ArrayLoadInst(name, arrayRef, index)
    fun getArrayLoad(arrayRef: Value, index: Value): Instruction = ArrayLoadInst(Slot(), arrayRef, index)

    fun getArrayStore(array: Value, index: Value, value: Value): Instruction = ArrayStoreInst(array, index, value)


    fun getFieldLoad(name: String, field: Field): Instruction = getFieldLoad(StringName(name), field)
    fun getFieldLoad(name: Name, field: Field): Instruction = FieldLoadInst(name, field)
    fun getFieldLoad(field: Field): Instruction = FieldLoadInst(Slot(), field)

    fun getFieldLoad(name: String, owner: Value, field: Field): Instruction = getFieldLoad(StringName(name), owner, field)
    fun getFieldLoad(name: Name, owner: Value, field: Field): Instruction = FieldLoadInst(name, owner, field)
    fun getFieldLoad(owner: Value, field: Field): Instruction = FieldLoadInst(Slot(), owner, field)

    fun getFieldStore(field: Field, value: Value): Instruction = FieldStoreInst(field, value)
    fun getFieldStore(owner: Value, field: Field, value: Value): Instruction = FieldStoreInst(owner, field, value)


    fun getBinary(name: String, opcode: BinaryOpcode, lhv: Value, rhv: Value): Instruction = getBinary(StringName(name), opcode, lhv, rhv)
    fun getBinary(name: Name, opcode: BinaryOpcode, lhv: Value, rhv: Value): Instruction = BinaryInst(name, opcode, lhv, rhv)
    fun getBinary(opcode: BinaryOpcode, lhv: Value, rhv: Value): Instruction = BinaryInst(Slot(), opcode, lhv, rhv)

    fun getCmp(name: String, opcode: CmpOpcode, lhv: Value, rhv: Value): Instruction = getCmp(StringName(name), opcode, lhv, rhv)
    fun getCmp(name: Name, opcode: CmpOpcode, lhv: Value, rhv: Value): Instruction = CmpInst(name, opcode, lhv, rhv)
    fun getCmp(opcode: CmpOpcode, lhv: Value, rhv: Value): Instruction = CmpInst(Slot(), opcode, lhv, rhv)

    fun getCast(name: String, type: Type, obj: Value): Instruction = getCast(StringName(name), type, obj)
    fun getCast(name: Name, type: Type, obj: Value): Instruction = CastInst(name, type, obj)
    fun getCast(type: Type, obj: Value): Instruction = CastInst(Slot(), type, obj)

    fun getInstanceOf(name: String, targetType: Type, obj: Value): Instruction = getInstanceOf(StringName(name), targetType, obj)
    fun getInstanceOf(name: Name, targetType: Type, obj: Value): Instruction = InstanceOfInst(name, targetType, obj)
    fun getInstanceOf(targetType: Type, obj: Value): Instruction = InstanceOfInst(Slot(), targetType, obj)

    fun getNew(name: String, type: Type): Instruction = getNew(StringName(name), type)
    fun getNew(name: String, `class`: Class): Instruction = getNew(StringName(name), `class`)
    fun getNew(name: Name, `class`: Class): Instruction = getNew(name, TF.getRefType(`class`))
    fun getNew(name: Name, type: Type): Instruction = NewInst(name, type)
    fun getNew(type: Type): Instruction = NewInst(Slot(), type)

    fun getUnary(name: String, opcode: UnaryOpcode, obj: Value): Instruction = getUnary(StringName(name), opcode, obj)
    fun getUnary(name: Name, opcode: UnaryOpcode, obj: Value): Instruction = UnaryInst(name, opcode, obj)
    fun getUnary(opcode: UnaryOpcode, obj: Value): Instruction = UnaryInst(Slot(), opcode, obj)


    fun getEnterMonitor(owner: Value): Instruction = EnterMonitorInst(owner)
    fun getExitMonitor(owner: Value): Instruction = ExitMonitorInst(owner)

    fun getJump(successor: BasicBlock): Instruction = JumpInst(successor)
    fun getBranch(cond: Value, trueSucc: BasicBlock, falseSucc: BasicBlock): Instruction = BranchInst(cond, trueSucc, falseSucc)
    fun getSwitch(key: Value, default: BasicBlock, branches: Map<Value, BasicBlock>): Instruction = SwitchInst(key, default, branches)
    fun getTableSwitch(index: Value, min: Value, max: Value, default: BasicBlock, branches: Array<BasicBlock>): Instruction =
            TableSwitchInst(index, min, max, default, branches)

    fun getPhi(name: String, type: Type, incomings: Map<BasicBlock, Value>): Instruction = getPhi(StringName(name), type, incomings)
    fun getPhi(name: Name, type: Type, incomings: Map<BasicBlock, Value>): Instruction = PhiInst(name, type, incomings)
    fun getPhi(type: Type, incomings: Map<BasicBlock, Value>): Instruction = PhiInst(Slot(), type, incomings)

    fun getCall(opcode: CallOpcode, method: Method, `class`: Class, args: Array<Value>): Instruction
            = CallInst(opcode, method, `class`, args)
    fun getCall(opcode: CallOpcode, method: Method, `class`: Class, obj: Value, args: Array<Value>): Instruction
            = CallInst(opcode, method, `class`, obj, args)

    fun getCall(opcode: CallOpcode, name: String, method: Method, `class`: Class, args: Array<Value>): Instruction
            = getCall(opcode, StringName(name), method, `class`, args)
    fun getCall(opcode: CallOpcode, name: Name, method: Method, `class`: Class, args: Array<Value>): Instruction
            = CallInst(opcode, name, method, `class`, args)

    fun getCall(opcode: CallOpcode, name: String, method: Method, `class`: Class, obj: Value, args: Array<Value>): Instruction
            = getCall(opcode, StringName(name), method, `class`, obj, args)
    fun getCall(opcode: CallOpcode, name: Name, method: Method, `class`: Class, obj: Value, args: Array<Value>): Instruction
            = CallInst(opcode, name, method, `class`, obj, args)

    fun getCatch(name: String, type: Type): Instruction = getCatch(StringName(name), type)
    fun getCatch(name: Name, type: Type): Instruction = CatchInst(name, type)
    fun getCatch(type: Type): Instruction = CatchInst(Slot(), type)
    fun getThrow(throwable: Value): Instruction = ThrowInst(throwable)

    fun getReturn(): Instruction = ReturnInst()
    fun getReturn(retval: Value): Instruction = ReturnInst(retval)
}