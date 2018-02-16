package org.jetbrains.research.kfg.value.expr

import org.jetbrains.research.kfg.type.ArrayType
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.value.Value

class ExprFactory {
    fun getArrayLoad(arrayRef: Value, index: Value): Expr {
        val type = arrayRef.type as ArrayType
        return ArrayLoadExpr(type, arrayRef, index)
    }

    fun getNewArray(type: Type, count: Value): Expr = NewArrayExpr(type, count)

    fun getNew(type: Type): Expr = NewExpr(type)

    fun getCheckCast(type: Type, obj: Value): Expr = CheckCastExpr(type, obj)

    fun getInstanceOf(obj: Value): Expr = InstanceOfExpr(obj)

    fun getBinary(opcode: BinaryOpcode, lhv: Value, rhv: Value): Expr = BinaryExpr(opcode, lhv, rhv)

    fun getCast(type: Type, obj: Value): Expr = CastExpr(type, obj)

    fun getCmp(opcode: CmpOpcode, lhv: Value, rhv: Value): Expr = CmpExpr(opcode, lhv, rhv)

    fun getUnary(opcode: UnaryOpcode, arrayRef: Value): Expr = UnaryExpr(opcode, arrayRef)
}