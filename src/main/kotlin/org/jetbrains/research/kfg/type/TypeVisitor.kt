package org.jetbrains.research.kfg.type

import com.abdullin.kthelper.assert.unreachable
import com.abdullin.kthelper.logging.log

interface TypeVisitor {
    val types: TypeFactory

    fun visit(type: Type): Type = when (type) {
        is VoidType -> visitVoid(type)
        is PrimaryType -> visitPrimary(type)
        is Reference -> visitReference(type)
        else -> unreachable { log.error("Unknown type: $type") }
    }

    fun visitVoid(type: VoidType): Type = type

    fun visitPrimary(type: PrimaryType) = when (type) {
        is Integral -> visitIntegral(type)
        is Real -> visitReal(type)
        else -> unreachable { log.error("Unknown primary type: $type") }
    }

    fun visitIntegral(type: Integral) = when (type) {
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
        else -> unreachable { log.error("Unknown reference type: $type") }
    }

    fun visitNull(type: NullType): Type = type
    fun visitClass(type: ClassType): Type = type
    fun visitArray(type: ArrayType): Type = type
}