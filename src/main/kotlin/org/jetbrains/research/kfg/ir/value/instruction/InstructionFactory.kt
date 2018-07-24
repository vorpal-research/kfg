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
    fun getNewArray(name: String, componentType: Type, count: Value) = getNewArray(StringName(name), TF.getArrayType(componentType), count)
    fun getNewArray(name: Name, componentType: Type, count: Value) = NewArrayInst(name, TF.getArrayType(componentType), arrayOf(count))
    fun getNewArray(componentType: Type, count: Value) = NewArrayInst(Slot(), TF.getArrayType(componentType), arrayOf(count))

    fun getNewArray(name: String, type: Type, dimensions: Array<Value>) = getNewArray(StringName(name), type, dimensions)
    fun getNewArray(name: Name, type: Type, dimensions: Array<Value>) = NewArrayInst(name, type, dimensions)
    fun getNewArray(type: Type, dimensions: Array<Value>) = NewArrayInst(Slot(), type, dimensions)

    fun getArrayLoad(name: String, arrayRef: Value, index: Value) = getArrayLoad(StringName(name), arrayRef, index)
    fun getArrayLoad(name: Name, arrayRef: Value, index: Value) = ArrayLoadInst(name, arrayRef, index)
    fun getArrayLoad(arrayRef: Value, index: Value) = ArrayLoadInst(Slot(), arrayRef, index)

    fun getArrayStore(array: Value, index: Value, value: Value) = ArrayStoreInst(array, index, value)


    fun getFieldLoad(name: String, field: Field) = getFieldLoad(StringName(name), field)
    fun getFieldLoad(name: Name, field: Field) = FieldLoadInst(name, field)
    fun getFieldLoad(field: Field) = FieldLoadInst(Slot(), field)

    fun getFieldLoad(name: String, owner: Value, field: Field) = getFieldLoad(StringName(name), owner, field)
    fun getFieldLoad(name: Name, owner: Value, field: Field) = FieldLoadInst(name, owner, field)
    fun getFieldLoad(owner: Value, field: Field) = FieldLoadInst(Slot(), owner, field)

    fun getFieldStore(field: Field, value: Value) = FieldStoreInst(field, value)
    fun getFieldStore(owner: Value, field: Field, value: Value) = FieldStoreInst(owner, field, value)


    fun getBinary(name: String, opcode: BinaryOpcode, lhv: Value, rhv: Value) = getBinary(StringName(name), opcode, lhv, rhv)
    fun getBinary(name: Name, opcode: BinaryOpcode, lhv: Value, rhv: Value) = BinaryInst(name, opcode, lhv, rhv)
    fun getBinary(opcode: BinaryOpcode, lhv: Value, rhv: Value) = BinaryInst(Slot(), opcode, lhv, rhv)

    fun getCmp(name: String, type: Type, opcode: CmpOpcode, lhv: Value, rhv: Value) =
            getCmp(StringName(name), type, opcode, lhv, rhv)
    fun getCmp(name: Name, type: Type, opcode: CmpOpcode, lhv: Value, rhv: Value) =
            CmpInst(name, type, opcode, lhv, rhv)
    fun getCmp(type: Type, opcode: CmpOpcode, lhv: Value, rhv: Value) =
            getCmp(Slot(), type, opcode, lhv, rhv)

    fun getCast(name: String, type: Type, obj: Value) = getCast(StringName(name), type, obj)
    fun getCast(name: Name, type: Type, obj: Value) = CastInst(name, type, obj)
    fun getCast(type: Type, obj: Value) = CastInst(Slot(), type, obj)

    fun getInstanceOf(name: String, targetType: Type, obj: Value) = getInstanceOf(StringName(name), targetType, obj)
    fun getInstanceOf(name: Name, targetType: Type, obj: Value) = InstanceOfInst(name, targetType, obj)
    fun getInstanceOf(targetType: Type, obj: Value) = InstanceOfInst(Slot(), targetType, obj)

    fun getNew(name: String, type: Type) = getNew(StringName(name), type)
    fun getNew(name: String, `class`: Class) = getNew(StringName(name), `class`)
    fun getNew(name: Name, `class`: Class) = getNew(name, TF.getRefType(`class`))
    fun getNew(name: Name, type: Type) = NewInst(name, type)
    fun getNew(type: Type) = NewInst(Slot(), type)

    fun getUnary(name: String, opcode: UnaryOpcode, obj: Value) = getUnary(StringName(name), opcode, obj)
    fun getUnary(name: Name, opcode: UnaryOpcode, obj: Value) = UnaryInst(name, opcode, obj)
    fun getUnary(opcode: UnaryOpcode, obj: Value) = UnaryInst(Slot(), opcode, obj)


    fun getEnterMonitor(owner: Value) = EnterMonitorInst(owner)
    fun getExitMonitor(owner: Value) = ExitMonitorInst(owner)

    fun getJump(successor: BasicBlock) = JumpInst(successor)
    fun getBranch(cond: Value, trueSucc: BasicBlock, falseSucc: BasicBlock) = BranchInst(cond, trueSucc, falseSucc)
    fun getSwitch(key: Value, default: BasicBlock, branches: Map<Value, BasicBlock>) = SwitchInst(key, default, branches)
    fun getTableSwitch(index: Value, min: Value, max: Value, default: BasicBlock, branches: Array<BasicBlock>) =
            TableSwitchInst(index, min, max, default, branches)

    fun getPhi(name: String, type: Type, incomings: Map<BasicBlock, Value>) = getPhi(StringName(name), type, incomings)
    fun getPhi(name: Name, type: Type, incomings: Map<BasicBlock, Value>) = PhiInst(name, type, incomings)
    fun getPhi(type: Type, incomings: Map<BasicBlock, Value>) = PhiInst(Slot(), type, incomings)

    fun getCall(opcode: CallOpcode, method: Method, `class`: Class, args: Array<Value>, isNamed: Boolean)
            = if (isNamed) CallInst(opcode, Slot(), method, `class`, args) else CallInst(opcode, method, `class`, args)
    fun getCall(opcode: CallOpcode, method: Method, `class`: Class, obj: Value, args: Array<Value>, isNamed: Boolean)
            = if (isNamed) CallInst(opcode, Slot(), method, `class`, obj, args) else CallInst(opcode, method, `class`, obj, args)

    fun getCall(opcode: CallOpcode, name: String, method: Method, `class`: Class, args: Array<Value>)
            = getCall(opcode, StringName(name), method, `class`, args)
    fun getCall(opcode: CallOpcode, name: Name, method: Method, `class`: Class, args: Array<Value>)
            = CallInst(opcode, name, method, `class`, args)

    fun getCall(opcode: CallOpcode, name: String, method: Method, `class`: Class, obj: Value, args: Array<Value>)
            = getCall(opcode, StringName(name), method, `class`, obj, args)
    fun getCall(opcode: CallOpcode, name: Name, method: Method, `class`: Class, obj: Value, args: Array<Value>)
            = CallInst(opcode, name, method, `class`, obj, args)

    fun getCatch(name: String, type: Type) = getCatch(StringName(name), type)
    fun getCatch(name: Name, type: Type) = CatchInst(name, type)
    fun getCatch(type: Type) = CatchInst(Slot(), type)
    fun getThrow(throwable: Value) = ThrowInst(throwable)

    fun getReturn() = ReturnInst()
    fun getReturn(retval: Value) = ReturnInst(retval)
}