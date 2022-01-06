package org.jetbrains.research.kfg.ir.value.instruction

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.ir.*
import org.jetbrains.research.kfg.ir.value.*
import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kthelper.collection.ListBuilder

class InstructionFactory internal constructor(val cm: ClassManager) {
    private val types get() = cm.type

    fun getNewArray(ctx: UsageContext, name: String, componentType: Type, count: Value): Instruction =
        getNewArray(ctx, StringName(name), types.getArrayType(componentType), count)

    fun getNewArray(ctx: UsageContext, name: Name, componentType: Type, count: Value): Instruction =
        NewArrayInst(name, types.getArrayType(componentType), arrayOf(count), ctx)

    fun getNewArray(ctx: UsageContext, componentType: Type, count: Value): Instruction =
        NewArrayInst(Slot(), types.getArrayType(componentType), arrayOf(count), ctx)

    fun getNewArray(ctx: UsageContext, name: String, type: Type, dimensions: Array<Value>): Instruction =
        getNewArray(ctx, StringName(name), type, dimensions)

    fun getNewArray(ctx: UsageContext, name: Name, type: Type, dimensions: Array<Value>): Instruction =
        NewArrayInst(name, type, dimensions, ctx)

    fun getNewArray(ctx: UsageContext, type: Type, dimensions: Array<Value>): Instruction =
        NewArrayInst(Slot(), type, dimensions, ctx)

    fun getArrayLoad(ctx: UsageContext, name: String, arrayRef: Value, index: Value): Instruction =
        getArrayLoad(ctx, StringName(name), arrayRef, index)

    fun getArrayLoad(ctx: UsageContext, name: Name, arrayRef: Value, index: Value): Instruction {
        val type = when {
            arrayRef.type === types.nullType -> types.nullType
            else -> (arrayRef.type as ArrayType).component
        }
        return ArrayLoadInst(name, type, arrayRef, index, ctx)
    }

    fun getArrayLoad(ctx: UsageContext, arrayRef: Value, index: Value): Instruction {
        val type = when {
            arrayRef.type === types.nullType -> types.nullType
            else -> (arrayRef.type as ArrayType).component
        }
        return ArrayLoadInst(Slot(), type, arrayRef, index, ctx)
    }

    fun getArrayStore(ctx: UsageContext, array: Value, index: Value, value: Value): Instruction =
        ArrayStoreInst(array, types.voidType, index, value, ctx)

    fun getFieldLoad(ctx: UsageContext, name: String, field: Field): Instruction =
        getFieldLoad(ctx, StringName(name), field)

    fun getFieldLoad(ctx: UsageContext, name: Name, field: Field): Instruction = FieldLoadInst(name, field, ctx)
    fun getFieldLoad(ctx: UsageContext, field: Field): Instruction = FieldLoadInst(Slot(), field, ctx)

    fun getFieldLoad(ctx: UsageContext, name: String, owner: Value, field: Field): Instruction =
        getFieldLoad(ctx, StringName(name), owner, field)

    fun getFieldLoad(ctx: UsageContext, name: Name, owner: Value, field: Field): Instruction =
        FieldLoadInst(name, owner, field, ctx)

    fun getFieldLoad(ctx: UsageContext, owner: Value, field: Field): Instruction =
        FieldLoadInst(Slot(), owner, field, ctx)

    fun getFieldStore(ctx: UsageContext, field: Field, value: Value): Instruction = FieldStoreInst(field, value, ctx)
    fun getFieldStore(ctx: UsageContext, owner: Value, field: Field, value: Value): Instruction =
        FieldStoreInst(owner, field, value, ctx)


    fun getBinary(ctx: UsageContext, name: String, opcode: BinaryOpcode, lhv: Value, rhv: Value): Instruction =
        getBinary(ctx, StringName(name), opcode, lhv, rhv)

    fun getBinary(ctx: UsageContext, name: Name, opcode: BinaryOpcode, lhv: Value, rhv: Value): Instruction =
        BinaryInst(name, opcode, lhv, rhv, ctx)

    fun getBinary(ctx: UsageContext, opcode: BinaryOpcode, lhv: Value, rhv: Value): Instruction =
        BinaryInst(Slot(), opcode, lhv, rhv, ctx)

    fun getCmp(ctx: UsageContext, name: String, type: Type, opcode: CmpOpcode, lhv: Value, rhv: Value): Instruction =
        getCmp(ctx, StringName(name), type, opcode, lhv, rhv)

    fun getCmp(ctx: UsageContext, name: Name, type: Type, opcode: CmpOpcode, lhv: Value, rhv: Value): Instruction =
        CmpInst(name, type, opcode, lhv, rhv, ctx)

    fun getCmp(ctx: UsageContext, type: Type, opcode: CmpOpcode, lhv: Value, rhv: Value): Instruction =
        getCmp(ctx, Slot(), type, opcode, lhv, rhv)

    fun getCast(ctx: UsageContext, name: String, type: Type, obj: Value): Instruction =
        getCast(ctx, StringName(name), type, obj)

    fun getCast(ctx: UsageContext, name: Name, type: Type, obj: Value): Instruction = CastInst(name, type, obj, ctx)
    fun getCast(ctx: UsageContext, type: Type, obj: Value): Instruction = CastInst(Slot(), type, obj, ctx)

    fun getInstanceOf(ctx: UsageContext, name: String, targetType: Type, obj: Value): Instruction =
        getInstanceOf(ctx, StringName(name), targetType, obj)

    fun getInstanceOf(ctx: UsageContext, name: Name, targetType: Type, obj: Value): Instruction =
        InstanceOfInst(name, types.boolType, targetType, obj, ctx)

    fun getInstanceOf(ctx: UsageContext, targetType: Type, obj: Value): Instruction =
        InstanceOfInst(Slot(), types.boolType, targetType, obj, ctx)

    fun getNew(ctx: UsageContext, name: String, type: Type): Instruction = getNew(ctx, StringName(name), type)
    fun getNew(ctx: UsageContext, name: String, `class`: Class): Instruction = getNew(ctx, StringName(name), `class`)
    fun getNew(ctx: UsageContext, name: Name, `class`: Class): Instruction =
        getNew(ctx, name, types.getRefType(`class`))

    fun getNew(ctx: UsageContext, name: Name, type: Type): Instruction = NewInst(name, type, ctx)
    fun getNew(ctx: UsageContext, type: Type): Instruction = NewInst(Slot(), type, ctx)

    fun getUnary(ctx: UsageContext, name: String, opcode: UnaryOpcode, obj: Value): Instruction =
        getUnary(ctx, StringName(name), opcode, obj)

    fun getUnary(ctx: UsageContext, name: Name, opcode: UnaryOpcode, obj: Value): Instruction {
        val type = if (opcode == UnaryOpcode.LENGTH) types.intType else obj.type
        return UnaryInst(name, type, opcode, obj, ctx)
    }

    fun getUnary(ctx: UsageContext, opcode: UnaryOpcode, obj: Value): Instruction {
        val type = if (opcode == UnaryOpcode.LENGTH) types.intType else obj.type
        return UnaryInst(Slot(), type, opcode, obj, ctx)
    }


    fun getEnterMonitor(ctx: UsageContext, owner: Value): Instruction = EnterMonitorInst(types.voidType, owner, ctx)
    fun getExitMonitor(ctx: UsageContext, owner: Value): Instruction = ExitMonitorInst(types.voidType, owner, ctx)

    fun getJump(ctx: UsageContext, successor: BasicBlock): Instruction = JumpInst(types.voidType, successor, ctx)
    fun getBranch(ctx: UsageContext, cond: Value, trueSucc: BasicBlock, falseSucc: BasicBlock): Instruction =
        BranchInst(cond, types.voidType, trueSucc, falseSucc, ctx)

    fun getSwitch(ctx: UsageContext, key: Value, default: BasicBlock, branches: Map<Value, BasicBlock>): Instruction =
        SwitchInst(key, types.voidType, default, branches, ctx)

    fun getTableSwitch(
        ctx: UsageContext,
        index: Value,
        min: Value,
        max: Value,
        default: BasicBlock,
        branches: Array<BasicBlock>
    ): Instruction =
        TableSwitchInst(types.voidType, index, min, max, default, branches, ctx)

    fun getPhi(ctx: UsageContext, name: String, type: Type, incomings: Map<BasicBlock, Value>): Instruction =
        getPhi(ctx, StringName(name), type, incomings)

    fun getPhi(ctx: UsageContext, name: Name, type: Type, incomings: Map<BasicBlock, Value>): Instruction =
        PhiInst(name, type, incomings, ctx)

    fun getPhi(ctx: UsageContext, type: Type, incomings: Map<BasicBlock, Value>): Instruction =
        PhiInst(Slot(), type, incomings, ctx)

    fun getCall(
        ctx: UsageContext,
        opcode: CallOpcode,
        method: Method,
        `class`: Class,
        args: Array<Value>,
        isNamed: Boolean
    ) =
        when {
            isNamed -> CallInst(opcode, Slot(), method, `class`, args, ctx)
            else -> CallInst(opcode, method, `class`, args, ctx)
        }

    fun getCall(
        ctx: UsageContext,
        opcode: CallOpcode,
        method: Method,
        `class`: Class,
        obj: Value,
        args: Array<Value>,
        isNamed: Boolean
    ) =
        when {
            isNamed -> CallInst(opcode, Slot(), method, `class`, obj, args, ctx)
            else -> CallInst(opcode, method, `class`, obj, args, ctx)
        }

    fun getCall(
        ctx: UsageContext,
        opcode: CallOpcode,
        name: String,
        method: Method,
        `class`: Class,
        args: Array<Value>
    ) =
        getCall(ctx, opcode, StringName(name), method, `class`, args)

    fun getCall(ctx: UsageContext, opcode: CallOpcode, name: Name, method: Method, `class`: Class, args: Array<Value>) =
        CallInst(opcode, name, method, `class`, args, ctx)

    fun getCall(
        ctx: UsageContext,
        opcode: CallOpcode,
        name: String,
        method: Method,
        `class`: Class,
        obj: Value,
        args: Array<Value>
    ) =
        getCall(ctx, opcode, StringName(name), method, `class`, obj, args)

    fun getCall(
        ctx: UsageContext,
        opcode: CallOpcode,
        name: Name,
        method: Method,
        `class`: Class,
        obj: Value,
        args: Array<Value>
    ) =
        CallInst(opcode, name, method, `class`, obj, args, ctx)


    fun getInvokeDynamic(
        ctx: UsageContext,
        name: Name,
        methodName: String,
        methodDesc: MethodDesc,
        bootstrapMethod: Handle,
        bootstrapMethodArgs: Array<Any>,
        operands: Array<Value>
    ) = InvokeDynamicInst(name, methodName, methodDesc, bootstrapMethod, bootstrapMethodArgs, operands, ctx)

    fun getInvokeDynamic(
        ctx: UsageContext,
        name: String,
        methodName: String,
        methodDesc: MethodDesc,
        bootstrapMethod: Handle,
        bootstrapMethodArgs: Array<Any>,
        operands: Array<Value>
    ) = getInvokeDynamic(ctx, StringName(name), methodName, methodDesc, bootstrapMethod, bootstrapMethodArgs, operands)

    fun getInvokeDynamic(
        ctx: UsageContext,
        methodName: String,
        methodDesc: MethodDesc,
        bootstrapMethod: Handle,
        bootstrapMethodArgs: Array<Any>,
        operands: Array<Value>
    ) = InvokeDynamicInst(methodName, methodDesc, bootstrapMethod, bootstrapMethodArgs, operands, ctx)

    fun getCatch(ctx: UsageContext, name: String, type: Type): Instruction = getCatch(ctx, StringName(name), type)
    fun getCatch(ctx: UsageContext, name: Name, type: Type): Instruction = CatchInst(name, type, ctx)
    fun getCatch(ctx: UsageContext, type: Type): Instruction = CatchInst(Slot(), type, ctx)
    fun getThrow(ctx: UsageContext, throwable: Value): Instruction = ThrowInst(types.voidType, throwable, ctx)

    fun getReturn(ctx: UsageContext): Instruction = ReturnInst(types.voidType, ctx)
    fun getReturn(ctx: UsageContext, retval: Value): Instruction = ReturnInst(retval, ctx)

    fun getUnreachable(ctx: UsageContext): Instruction = UnreachableInst(types.voidType, ctx)

    fun getUnknownValueInst(ctx: UsageContext, name: String) =
        UnknownValueInst(ctx, StringName(name), cm)
}

interface InstructionBuilder {
    val cm: ClassManager
    val ctx: UsageContext

    val values get() = cm.value
    val instructions get() = cm.instruction
    val types get() = cm.type

    /**
     * value wrappers
     */
    val Boolean.asValue get() = values.getBool(this)
    val Number.asValue get() = values.getNumber(this)
    val String.asValue get() = values.getString(this)

    val Type.asArray get() = types.getArrayType(this)

    /**
     * new array wrappers
     */
    fun Type.newArray(name: String, length: Int) = this.newArray(name, values.getInt(length))
    fun Type.newArray(name: Name, length: Int) = this.newArray(name, values.getInt(length))

    fun Type.newArray(length: Int) = instructions.getNewArray(ctx, this, values.getInt(length))
    fun Type.newArray(length: Value) = instructions.getNewArray(ctx, this, length)

    fun Type.newArray(name: String, length: Value) =
        instructions.getNewArray(ctx, name, this, length)

    fun Type.newArray(name: Name, length: Value) =
        instructions.getNewArray(ctx, name, this, length)


    fun Type.newArray(name: String, dimensions: Array<Int>) =
        this.newArray(name, dimensions.map { values.getInt(it) }.toTypedArray())

    fun Type.newArray(name: Name, dimensions: Array<Int>) =
        this.newArray(name, dimensions.map { values.getInt(it) }.toTypedArray())

    fun Type.newArray(dimensions: Array<Int>) =
        this.newArray(dimensions.map { values.getInt(it) }.toTypedArray())

    fun Type.newArray(name: String, dimensions: List<Value>) = newArray(name, dimensions.toTypedArray())
    fun Type.newArray(name: String, dimensions: Array<Value>) =
        instructions.getNewArray(ctx, name, this, dimensions)

    fun Type.newArray(name: Name, dimensions: List<Value>) = newArray(name, dimensions.toTypedArray())
    fun Type.newArray(name: Name, dimensions: Array<Value>) =
        instructions.getNewArray(ctx, name, this, dimensions)

    fun Type.newArray(dimensions: Array<Value>) =
        instructions.getNewArray(ctx, this, dimensions)

    fun Type.newArray(dimensions: List<Value>) =
        newArray(dimensions.toTypedArray())

    /**
     * array load wrappers
     */
    fun Value.load(name: String, index: Value): Instruction =
        instructions.getArrayLoad(ctx, name, this, index)

    fun Value.load(name: Name, index: Value): Instruction =
        instructions.getArrayLoad(ctx, name, this, index)

    operator fun Value.get(index: Value): Instruction =
        instructions.getArrayLoad(ctx, this, index)

    fun Value.load(name: String, index: Int): Instruction =
        this.load(name, values.getInt(index))

    fun Value.load(name: Name, index: Int): Instruction =
        this.load(name, values.getInt(index))

    operator fun Value.get(index: Int): Instruction =
        this[values.getInt(index)]

    /**
     * array store wrappers
     */
    fun Value.store(index: Value, value: Value) =
        instructions.getArrayStore(ctx, this, index, value)

    fun Value.store(index: Int, value: Value) =
        instructions.getArrayStore(ctx, this, values.getInt(index), value)

    /**
     * field load wrappers
     */
    fun Field.load(name: String) = instructions.getFieldLoad(ctx, name, this)
    fun Field.load(name: Name) = instructions.getFieldLoad(ctx, name, this)
    fun Field.load() = instructions.getFieldLoad(ctx, this)

    fun Value.load(name: String, field: Field) = instructions.getFieldLoad(ctx, name, this, field)
    fun Value.load(name: Name, field: Field) = instructions.getFieldLoad(ctx, name, this, field)
    fun Value.load(field: Field) = instructions.getFieldLoad(ctx, this, field)

    /**
     * field store wrappers
     */
    fun Field.store(value: Value) = instructions.getFieldStore(ctx, this, value)
    fun Value.store(field: Field, value: Value) = instructions.getFieldStore(ctx, this, field, value)

    /**
     * binary operator wrappers
     */
    fun binary(name: String, opcode: BinaryOpcode, lhv: Value, rhv: Value) =
        instructions.getBinary(ctx, name, opcode, lhv, rhv)

    fun binary(name: Name, opcode: BinaryOpcode, lhv: Value, rhv: Value) =
        instructions.getBinary(ctx, name, opcode, lhv, rhv)

    fun binary(opcode: BinaryOpcode, lhv: Value, rhv: Value) =
        instructions.getBinary(ctx, opcode, lhv, rhv)

    operator fun Value.plus(rhv: Value) = binary(BinaryOpcode.ADD, this, rhv)
    operator fun Value.plus(rhv: Number) = binary(BinaryOpcode.ADD, this, values.getNumber(rhv))
    operator fun Value.minus(rhv: Value) = binary(BinaryOpcode.SUB, this, rhv)
    operator fun Value.minus(rhv: Number) = binary(BinaryOpcode.SUB, this, values.getNumber(rhv))
    operator fun Value.times(rhv: Value) = binary(BinaryOpcode.MUL, this, rhv)
    operator fun Value.times(rhv: Number) = binary(BinaryOpcode.MUL, this, values.getNumber(rhv))
    operator fun Value.div(rhv: Value) = binary(BinaryOpcode.DIV, this, rhv)
    operator fun Value.div(rhv: Number) = binary(BinaryOpcode.DIV, this, values.getNumber(rhv))
    operator fun Value.rem(rhv: Value) = binary(BinaryOpcode.REM, this, rhv)
    operator fun Value.rem(rhv: Number) = binary(BinaryOpcode.REM, this, values.getNumber(rhv))
    infix fun Value.shl(rhv: Value) = binary(BinaryOpcode.SHL, this, rhv)
    infix fun Value.shl(rhv: Number) = binary(BinaryOpcode.SHL, this, values.getNumber(rhv))
    infix fun Value.shr(rhv: Value) = binary(BinaryOpcode.SHR, this, rhv)
    infix fun Value.shr(rhv: Number) = binary(BinaryOpcode.SHR, this, values.getNumber(rhv))
    infix fun Value.ushr(rhv: Value) = binary(BinaryOpcode.USHR, this, rhv)
    infix fun Value.ushr(rhv: Number) = binary(BinaryOpcode.USHR, this, values.getNumber(rhv))
    infix fun Value.and(rhv: Value) = binary(BinaryOpcode.AND, this, rhv)
    infix fun Value.and(rhv: Number) = binary(BinaryOpcode.AND, this, values.getNumber(rhv))
    infix fun Value.or(rhv: Value) = binary(BinaryOpcode.OR, this, rhv)
    infix fun Value.or(rhv: Number) = binary(BinaryOpcode.OR, this, values.getNumber(rhv))
    infix fun Value.xor(rhv: Value) = binary(BinaryOpcode.XOR, this, rhv)
    infix fun Value.xor(rhv: Number) = binary(BinaryOpcode.XOR, this, values.getNumber(rhv))

    operator fun Number.plus(rhv: Value) = binary(BinaryOpcode.ADD, values.getNumber(this), rhv)
    operator fun Number.minus(rhv: Value) = binary(BinaryOpcode.SUB, values.getNumber(this), rhv)
    operator fun Number.times(rhv: Value) = binary(BinaryOpcode.MUL, values.getNumber(this), rhv)
    operator fun Number.div(rhv: Value) = binary(BinaryOpcode.DIV, values.getNumber(this), rhv)
    operator fun Number.rem(rhv: Value) = binary(BinaryOpcode.REM, values.getNumber(this), rhv)
    infix fun Number.shl(rhv: Value) = binary(BinaryOpcode.SHL, values.getNumber(this), rhv)
    infix fun Number.shr(rhv: Value) = binary(BinaryOpcode.SHR, values.getNumber(this), rhv)
    infix fun Number.ushr(rhv: Value) = binary(BinaryOpcode.USHR, values.getNumber(this), rhv)
    infix fun Number.and(rhv: Value) = binary(BinaryOpcode.AND, values.getNumber(this), rhv)
    infix fun Number.or(rhv: Value) = binary(BinaryOpcode.OR, values.getNumber(this), rhv)
    infix fun Number.xor(rhv: Value) = binary(BinaryOpcode.XOR, values.getNumber(this), rhv)

    /**
     * cmp operator wrappers
     */
    fun cmp(type: Type, opcode: CmpOpcode, lhv: Value, rhv: Value) = instructions.getCmp(ctx, type, opcode, lhv, rhv)
    fun cmp(name: String, type: Type, opcode: CmpOpcode, lhv: Value, rhv: Value) =
        instructions.getCmp(ctx, name, type, opcode, lhv, rhv)

    fun cmp(name: Name, type: Type, opcode: CmpOpcode, lhv: Value, rhv: Value) =
        instructions.getCmp(ctx, name, type, opcode, lhv, rhv)

    fun cmp(opcode: CmpOpcode, lhv: Value, rhv: Value) = cmp(getCmpResultType(types, opcode), opcode, lhv, rhv)
    fun cmp(name: String, opcode: CmpOpcode, lhv: Value, rhv: Value) =
        cmp(name, getCmpResultType(types, opcode), opcode, lhv, rhv)

    fun cmp(name: Name, opcode: CmpOpcode, lhv: Value, rhv: Value) =
        cmp(name, getCmpResultType(types, opcode), opcode, lhv, rhv)

    infix fun Value.eq(rhv: Value) = cmp(CmpOpcode.EQ, this, rhv)
    infix fun Value.eq(rhv: Number) = cmp(CmpOpcode.EQ, this, values.getNumber(rhv))
    infix fun Value.neq(rhv: Value) = cmp(CmpOpcode.NEQ, this, rhv)
    infix fun Value.neq(rhv: Number) = cmp(CmpOpcode.NEQ, this, values.getNumber(rhv))
    infix fun Value.lt(rhv: Value) = cmp(CmpOpcode.LT, this, rhv)
    infix fun Value.lt(rhv: Number) = cmp(CmpOpcode.LT, this, values.getNumber(rhv))
    infix fun Value.gt(rhv: Value) = cmp(CmpOpcode.GT, this, rhv)
    infix fun Value.gt(rhv: Number) = cmp(CmpOpcode.GT, this, values.getNumber(rhv))
    infix fun Value.le(rhv: Value) = cmp(CmpOpcode.LE, this, rhv)
    infix fun Value.le(rhv: Number) = cmp(CmpOpcode.LE, this, values.getNumber(rhv))
    infix fun Value.ge(rhv: Value) = cmp(CmpOpcode.GE, this, rhv)
    infix fun Value.ge(rhv: Number) = cmp(CmpOpcode.GE, this, values.getNumber(rhv))
    infix fun Value.cmp(rhv: Value) = cmp(CmpOpcode.CMP, this, rhv)
    infix fun Value.cmp(rhv: Number) = cmp(CmpOpcode.CMP, this, values.getNumber(rhv))
    infix fun Value.cmpg(rhv: Value) = cmp(CmpOpcode.CMPG, this, rhv)
    infix fun Value.cmpg(rhv: Number) = cmp(CmpOpcode.CMPG, this, values.getNumber(rhv))
    infix fun Value.cmpl(rhv: Value) = cmp(CmpOpcode.CMPL, this, rhv)
    infix fun Value.cmpl(rhv: Number) = cmp(CmpOpcode.CMPL, this, values.getNumber(rhv))

    infix fun Number.eq(rhv: Value) = cmp(CmpOpcode.EQ, values.getNumber(this), rhv)
    infix fun Number.neq(rhv: Value) = cmp(CmpOpcode.NEQ, values.getNumber(this), rhv)
    infix fun Number.lt(rhv: Value) = cmp(CmpOpcode.LT, values.getNumber(this), rhv)
    infix fun Number.gt(rhv: Value) = cmp(CmpOpcode.GT, values.getNumber(this), rhv)
    infix fun Number.le(rhv: Value) = cmp(CmpOpcode.LE, values.getNumber(this), rhv)
    infix fun Number.ge(rhv: Value) = cmp(CmpOpcode.GE, values.getNumber(this), rhv)
    infix fun Number.cmp(rhv: Value) = cmp(CmpOpcode.CMP, values.getNumber(this), rhv)
    infix fun Number.cmpg(rhv: Value) = cmp(CmpOpcode.CMPG, values.getNumber(this), rhv)
    infix fun Number.cmpl(rhv: Value) = cmp(CmpOpcode.CMPL, values.getNumber(this), rhv)

    /**
     * cast wrappers
     */
    fun Value.cast(name: String, type: Type) = instructions.getCast(ctx, name, type, this)
    fun Value.cast(name: Name, type: Type) = instructions.getCast(ctx, name, type, this)
    fun Value.cast(type: Type) = instructions.getCast(ctx, type, this)

    infix fun Value.`as`(type: Type) = this.cast(type)

    /**
     * instanceof wrappers
     */
    fun Value.instanceOf(name: String, type: Type) = instructions.getInstanceOf(ctx, name, type, this)
    fun Value.instanceOf(name: Name, type: Type) = instructions.getInstanceOf(ctx, name, type, this)
    fun Value.instanceOf(type: Type) = instructions.getInstanceOf(ctx, type, this)

    infix fun Value.`is`(type: Type) = this.instanceOf(type)

    /**
     * new wrappers
     */
    fun Type.new() = instructions.getNew(ctx, this)
    fun Type.new(name: String) = instructions.getNew(ctx, name, this)
    fun Type.new(name: Name) = instructions.getNew(ctx, name, this)

    fun Class.new() = toType().new()
    fun Class.new(name: String) = toType().new(name)
    fun Class.new(name: Name) = toType().new(name)

    /**
     * unary operator wrappers
     */
    fun Value.unary(opcode: UnaryOpcode) = instructions.getUnary(ctx, opcode, this)
    fun Value.unary(name: String, opcode: UnaryOpcode) = instructions.getUnary(ctx, name, opcode, this)
    fun Value.unary(name: Name, opcode: UnaryOpcode) = instructions.getUnary(ctx, name, opcode, this)

    operator fun Value.unaryMinus() = this.unary(UnaryOpcode.NEG)
    val Value.length get() = this.unary(UnaryOpcode.LENGTH)

    /**
     * monitor wrappers
     */
    fun Value.lock() = instructions.getEnterMonitor(ctx, this)
    fun Value.unlock() = instructions.getExitMonitor(ctx, this)

    /**
     * termination wrappers
     */
    fun goto(successor: BasicBlock) = instructions.getJump(ctx, successor)
    fun ite(cond: Value, trueBranch: BasicBlock, falseBranch: BasicBlock) =
        instructions.getBranch(ctx, cond, trueBranch, falseBranch)

    fun Int.switch(branches: Map<Value, BasicBlock>, default: BasicBlock) =
        values.getInt(this).switch(branches, default)

    fun Value.switch(branches: Map<Value, BasicBlock>, default: BasicBlock) =
        instructions.getSwitch(ctx, this, default, branches)

    fun Int.tableSwitch(range: IntRange, branches: Array<BasicBlock>, default: BasicBlock) =
        values.getInt(this).tableSwitch(range, branches, default)

    fun Value.tableSwitch(range: IntRange, branches: Array<BasicBlock>, default: BasicBlock) =
        this.tableSwitch(values.getInt(range.first), values.getInt(range.last), branches, default)

    fun Value.tableSwitch(min: Value, max: Value, branches: Array<BasicBlock>, default: BasicBlock) =
        instructions.getTableSwitch(ctx, this, min, max, default, branches)

    /**
     * phi wrappers
     */
    fun phi(type: Type, incomings: Map<BasicBlock, Value>) = instructions.getPhi(ctx, type, incomings)
    fun phi(name: String, type: Type, incomings: Map<BasicBlock, Value>) =
        instructions.getPhi(ctx, name, type, incomings)

    fun phi(name: Name, type: Type, incomings: Map<BasicBlock, Value>) = instructions.getPhi(ctx, name, type, incomings)

    fun Method.call(klass: Class, opcode: CallOpcode, args: Array<Value>) =
        instructions.getCall(ctx, opcode, this, klass, args, !returnType.isVoid)

    fun Method.call(klass: Class, name: String, opcode: CallOpcode, args: Array<Value>) =
        instructions.getCall(ctx, opcode, name, this, klass, args)

    fun Method.call(klass: Class, name: Name, opcode: CallOpcode, args: Array<Value>) =
        instructions.getCall(ctx, opcode, name, this, klass, args)

    fun Method.call(klass: Class, opcode: CallOpcode, instance: Value, args: Array<Value>) =
        instructions.getCall(ctx, opcode, this, klass, instance, args, !returnType.isVoid)

    fun Method.call(klass: Class, name: String, opcode: CallOpcode, instance: Value, args: Array<Value>) =
        instructions.getCall(ctx, opcode, name, this, klass, instance, args)

    fun Method.call(klass: Class, name: Name, opcode: CallOpcode, instance: Value, args: Array<Value>) =
        instructions.getCall(ctx, opcode, name, this, klass, instance, args)

    fun Method.staticCall(klass: Class, args: Array<Value>) = call(klass, CallOpcode.STATIC, args)
    fun Method.staticCall(klass: Class, args: List<Value>) = staticCall(klass, args.toTypedArray())

    fun Method.virtualCall(klass: Class, instance: Value, args: Array<Value>) =
        call(klass, CallOpcode.VIRTUAL, instance, args)

    fun Method.virtualCall(klass: Class, instance: Value, args: List<Value>) =
        virtualCall(klass, instance, args.toTypedArray())

    fun Method.interfaceCall(klass: Class, instance: Value, args: Array<Value>) =
        call(klass, CallOpcode.INTERFACE, instance, args)

    fun Method.interfaceCall(klass: Class, instance: Value, args: List<Value>) =
        interfaceCall(klass, instance, args.toTypedArray())

    fun Method.specialCall(klass: Class, instance: Value, args: Array<Value>) =
        call(klass, CallOpcode.SPECIAL, instance, args)

    fun Method.specialCall(klass: Class, instance: Value, args: List<Value>) =
        specialCall(klass, instance, args.toTypedArray())

    fun Method.staticCall(klass: Class, name: String, args: Array<Value>) = call(klass, name, CallOpcode.STATIC, args)
    fun Method.staticCall(klass: Class, name: String, args: List<Value>) = staticCall(klass, name, args.toTypedArray())

    fun Method.virtualCall(klass: Class, name: String, instance: Value, args: Array<Value>) =
        call(klass, name, CallOpcode.VIRTUAL, instance, args)

    fun Method.virtualCall(klass: Class, name: String, instance: Value, args: List<Value>) =
        virtualCall(klass, name, instance, args.toTypedArray())

    fun Method.interfaceCall(klass: Class, name: String, instance: Value, args: Array<Value>) =
        call(klass, name, CallOpcode.INTERFACE, instance, args)

    fun Method.interfaceCall(klass: Class, name: String, instance: Value, args: List<Value>) =
        interfaceCall(klass, name, instance, args.toTypedArray())

    fun Method.specialCall(klass: Class, name: String, instance: Value, args: Array<Value>) =
        call(klass, name, CallOpcode.SPECIAL, instance, args)

    fun Method.specialCall(klass: Class, name: String, instance: Value, args: List<Value>) =
        specialCall(klass, name, instance, args.toTypedArray())

    fun Method.staticCall(klass: Class, name: Name, args: Array<Value>) = call(klass, name, CallOpcode.STATIC, args)
    fun Method.staticCall(klass: Class, name: Name, args: List<Value>) = staticCall(klass, name, args.toTypedArray())
    fun Method.virtualCall(klass: Class, name: Name, instance: Value, args: Array<Value>) =
        call(klass, name, CallOpcode.VIRTUAL, instance, args)

    fun Method.virtualCall(klass: Class, name: Name, instance: Value, args: List<Value>) =
        virtualCall(klass, name, instance, args.toTypedArray())

    fun Method.interfaceCall(klass: Class, name: Name, instance: Value, args: Array<Value>) =
        call(klass, name, CallOpcode.INTERFACE, instance, args)

    fun Method.interfaceCall(klass: Class, name: Name, instance: Value, args: List<Value>) =
        interfaceCall(klass, name, instance, args.toTypedArray())

    fun Method.specialCall(klass: Class, name: Name, instance: Value, args: Array<Value>) =
        call(klass, name, CallOpcode.SPECIAL, instance, args)

    fun Method.specialCall(klass: Class, name: Name, instance: Value, args: List<Value>) =
        specialCall(klass, name, instance, args.toTypedArray())

    fun invokeDynamic(
        name: Name,
        methodName: String,
        methodDesc: MethodDesc,
        bootstrapMethod: Handle,
        bootstrapMethodArgs: Array<Any>,
        operands: Array<Value>
    ) = instructions.getInvokeDynamic(ctx, name, methodName, methodDesc, bootstrapMethod, bootstrapMethodArgs, operands)

    fun invokeDynamic(
        name: String,
        methodName: String,
        methodDesc: MethodDesc,
        bootstrapMethod: Handle,
        bootstrapMethodArgs: Array<Any>,
        operands: Array<Value>
    ) = instructions.getInvokeDynamic(ctx, name, methodName, methodDesc, bootstrapMethod, bootstrapMethodArgs, operands)

    fun invokeDynamic(
        methodName: String,
        methodDesc: MethodDesc,
        bootstrapMethod: Handle,
        bootstrapMethodArgs: Array<Any>,
        operands: Array<Value>
    ) = instructions.getInvokeDynamic(ctx, methodName, methodDesc, bootstrapMethod, bootstrapMethodArgs, operands)

    /**
     * catch/throw wrappers
     */
    fun Type.catch() = instructions.getCatch(ctx, this)
    fun Type.catch(name: String) = instructions.getCatch(ctx, name, this)
    fun Type.catch(name: Name) = instructions.getCatch(ctx, name, this)

    fun Value.`throw`() = instructions.getThrow(ctx, this)

    /**
     * return wrappers
     */
    fun `return`() = instructions.getReturn(ctx)
    fun `return`(retval: Value) = instructions.getReturn(ctx, retval)

    /**
     * unreachable wrapper
     */
    fun unreachable() = instructions.getUnreachable(ctx)
}

class InstructionBuilderImpl(
    override val cm: ClassManager,
    override val ctx: UsageContext
) : InstructionBuilder

class InstructionListBuilderImpl(
    override val cm: ClassManager,
    override val ctx: UsageContext
) : ListBuilder<Instruction>(), InstructionBuilder

fun inst(cm: ClassManager, ctx: UsageContext, body: InstructionBuilder.() -> Instruction): Instruction =
    InstructionBuilderImpl(cm, ctx).body()

fun insts(cm: ClassManager, ctx: UsageContext, body: InstructionListBuilderImpl.() -> List<Instruction>): List<Instruction> =
    InstructionListBuilderImpl(cm, ctx).body()