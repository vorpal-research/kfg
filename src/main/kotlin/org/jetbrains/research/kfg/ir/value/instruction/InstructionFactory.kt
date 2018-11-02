package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.BasicBlock
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.Field
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.StringName
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.ir.value.Name
import org.jetbrains.research.kfg.ir.value.Slot
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.Type

class InstructionFactory(val cm: ClassManager) {
    private val types get() = cm.type

    fun getNewArray(name: String, componentType: Type, count: Value): Instruction = getNewArray(StringName(name), types.getArrayType(componentType), count)
    fun getNewArray(name: Name, componentType: Type, count: Value): Instruction = NewArrayInst(name, types.getArrayType(componentType), arrayOf(count))
    fun getNewArray(componentType: Type, count: Value): Instruction = NewArrayInst(Slot(), types.getArrayType(componentType), arrayOf(count))

    fun getNewArray(name: String, type: Type, dimensions: Array<Value>): Instruction = getNewArray(StringName(name), type, dimensions)
    fun getNewArray(name: Name, type: Type, dimensions: Array<Value>): Instruction = NewArrayInst(name, type, dimensions)
    fun getNewArray(type: Type, dimensions: Array<Value>): Instruction = NewArrayInst(Slot(), type, dimensions)

    fun getArrayLoad(name: String, arrayRef: Value, index: Value): Instruction = getArrayLoad(StringName(name), arrayRef, index)
    fun getArrayLoad(name: Name, arrayRef: Value, index: Value): Instruction {
        val type = when {
            arrayRef.type === types.nullType -> types.nullType
            else -> (arrayRef.type as ArrayType).component
        }
        return ArrayLoadInst(name, type, arrayRef, index)
    }

    fun getArrayLoad(arrayRef: Value, index: Value): Instruction {
        val type = when {
            arrayRef.type === types.nullType -> types.nullType
            else -> (arrayRef.type as ArrayType).component
        }
        return ArrayLoadInst(Slot(), type, arrayRef, index)
    }

    fun getArrayStore(array: Value, index: Value, value: Value): Instruction = ArrayStoreInst(array, types.voidType, index, value)


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

    fun getCmp(name: String, type: Type, opcode: CmpOpcode, lhv: Value, rhv: Value): Instruction =
            getCmp(StringName(name), type, opcode, lhv, rhv)

    fun getCmp(name: Name, type: Type, opcode: CmpOpcode, lhv: Value, rhv: Value): Instruction =
            CmpInst(name, type, opcode, lhv, rhv)

    fun getCmp(type: Type, opcode: CmpOpcode, lhv: Value, rhv: Value): Instruction =
            getCmp(Slot(), type, opcode, lhv, rhv)

    fun getCast(name: String, type: Type, obj: Value): Instruction = getCast(StringName(name), type, obj)
    fun getCast(name: Name, type: Type, obj: Value): Instruction = CastInst(name, type, obj)
    fun getCast(type: Type, obj: Value): Instruction = CastInst(Slot(), type, obj)

    fun getInstanceOf(name: String, targetType: Type, obj: Value): Instruction = getInstanceOf(StringName(name), targetType, obj)
    fun getInstanceOf(name: Name, targetType: Type, obj: Value): Instruction = InstanceOfInst(name, types.boolType, targetType, obj)
    fun getInstanceOf(targetType: Type, obj: Value): Instruction = InstanceOfInst(Slot(), types.boolType, targetType, obj)

    fun getNew(name: String, type: Type): Instruction = getNew(StringName(name), type)
    fun getNew(name: String, `class`: Class): Instruction = getNew(StringName(name), `class`)
    fun getNew(name: Name, `class`: Class): Instruction = getNew(name, types.getRefType(`class`))
    fun getNew(name: Name, type: Type): Instruction = NewInst(name, type)
    fun getNew(type: Type): Instruction = NewInst(Slot(), type)

    fun getUnary(name: String, opcode: UnaryOpcode, obj: Value): Instruction = getUnary(StringName(name), opcode, obj)
    fun getUnary(name: Name, opcode: UnaryOpcode, obj: Value): Instruction {
        val type = if (opcode == UnaryOpcode.LENGTH) types.intType else obj.type
        return UnaryInst(name, type, opcode, obj)
    }
    fun getUnary(opcode: UnaryOpcode, obj: Value): Instruction {
        val type = if (opcode == UnaryOpcode.LENGTH) types.intType else obj.type
        return UnaryInst(Slot(), type, opcode, obj)
    }


    fun getEnterMonitor(owner: Value): Instruction = EnterMonitorInst(types.voidType, owner)
    fun getExitMonitor(owner: Value): Instruction = ExitMonitorInst(types.voidType, owner)

    fun getJump(successor: BasicBlock): Instruction = JumpInst(types.voidType, successor)
    fun getBranch(cond: Value, trueSucc: BasicBlock, falseSucc: BasicBlock): Instruction = BranchInst(cond, types.voidType, trueSucc, falseSucc)
    fun getSwitch(key: Value, default: BasicBlock, branches: Map<Value, BasicBlock>): Instruction = SwitchInst(key, types.voidType, default, branches)
    fun getTableSwitch(index: Value, min: Value, max: Value, default: BasicBlock, branches: Array<BasicBlock>): Instruction =
            TableSwitchInst(types.voidType, index, min, max, default, branches)

    fun getPhi(name: String, type: Type, incomings: Map<BasicBlock, Value>): Instruction = getPhi(StringName(name), type, incomings)
    fun getPhi(name: Name, type: Type, incomings: Map<BasicBlock, Value>): Instruction = PhiInst(name, type, incomings)
    fun getPhi(type: Type, incomings: Map<BasicBlock, Value>): Instruction = PhiInst(Slot(), type, incomings)

    fun getCall(opcode: CallOpcode, method: Method, `class`: Class, args: Array<Value>, isNamed: Boolean) = if (isNamed) CallInst(opcode, Slot(), method, `class`, args) else CallInst(opcode, method, `class`, args)
    fun getCall(opcode: CallOpcode, method: Method, `class`: Class, obj: Value, args: Array<Value>, isNamed: Boolean) = if (isNamed) CallInst(opcode, Slot(), method, `class`, obj, args) else CallInst(opcode, method, `class`, obj, args)

    fun getCall(opcode: CallOpcode, name: String, method: Method, `class`: Class, args: Array<Value>) = getCall(opcode, StringName(name), method, `class`, args)
    fun getCall(opcode: CallOpcode, name: Name, method: Method, `class`: Class, args: Array<Value>) = CallInst(opcode, name, method, `class`, args)

    fun getCall(opcode: CallOpcode, name: String, method: Method, `class`: Class, obj: Value, args: Array<Value>) = getCall(opcode, StringName(name), method, `class`, obj, args)
    fun getCall(opcode: CallOpcode, name: Name, method: Method, `class`: Class, obj: Value, args: Array<Value>) = CallInst(opcode, name, method, `class`, obj, args)

    fun getCatch(name: String, type: Type): Instruction = getCatch(StringName(name), type)
    fun getCatch(name: Name, type: Type): Instruction = CatchInst(name, type)
    fun getCatch(type: Type): Instruction = CatchInst(Slot(), type)
    fun getThrow(throwable: Value): Instruction = ThrowInst(types.voidType, throwable)

    fun getReturn(): Instruction = ReturnInst(types.voidType)
    fun getReturn(retval: Value): Instruction = ReturnInst(retval)

    fun getUnreachable(): Instruction = UnreachableInst(types.voidType)
}