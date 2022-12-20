package org.vorpal.research.kfg.type

import org.vorpal.research.kthelper.assert.unreachable

interface TypeVisitor {
    val types: TypeFactory

    fun visit(type: Type): Type = when (type) {
        is VoidType -> visitVoid(type)
        is PrimitiveType -> visitPrimary(type)
        is Reference -> visitReference(type)
        else -> unreachable("Unknown type: $type")
    }

    fun visitVoid(type: VoidType): Type = type

    fun visitPrimary(type: PrimitiveType) = when (type) {
        is Integer -> visitIntegral(type)
        is Real -> visitReal(type)
    }

    fun visitIntegral(type: Integer) = when (type) {
        is BoolType -> visitBool(type)
        is ByteType -> visitByte(type)
        is ShortType -> visitShort(type)
        is IntType -> visitInt(type)
        is LongType -> visitLong(type)
        is CharType -> visitChar(type)
    }

    fun visitBool(type: BoolType): Type = type
    fun visitByte(type: ByteType): Type = type
    fun visitShort(type: ShortType): Type = type
    fun visitChar(type: CharType): Type = type
    fun visitInt(type: IntType): Type = type
    fun visitLong(type: LongType): Type = type

    fun visitReal(type: Real) = when (type) {
        is FloatType -> visitFloat(type)
        is DoubleType -> visitDouble(type)
    }

    fun visitFloat(type: FloatType): Type = type
    fun visitDouble(type: DoubleType): Type = type

    fun visitReference(type: Reference): Type = when (type) {
        is NullType -> visitNull(type)
        is ClassType -> visitClass(type)
        is ArrayType -> visitArray(type)
    }

    fun visitNull(type: NullType): Type = type
    fun visitClass(type: ClassType): Type = type
    fun visitArray(type: ArrayType): Type = type
}
