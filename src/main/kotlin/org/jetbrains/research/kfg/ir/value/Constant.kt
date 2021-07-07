package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.Type

sealed class Constant(name: String, type: Type) : Value(ConstantName(name), type)

class BoolConstant(val value: Boolean, type: Type) : Constant(value.toString(), type) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BoolConstant

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int = value.hashCode()
}

class ByteConstant(val value: Byte, type: Type) : Constant(value.toString(), type) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ByteConstant

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int = value.hashCode()
}

class ShortConstant(val value: Short, type: Type) : Constant(value.toString(), type) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ShortConstant

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int = value.hashCode()
}

class IntConstant(val value: Int, type: Type) : Constant(value.toString(), type) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IntConstant

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int = value.hashCode()
}

class LongConstant(val value: Long, type: Type) : Constant(value.toString(), type) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LongConstant

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int = value.hashCode()
}

class CharConstant(val value: Char, type: Type) : Constant(value.toString(), type) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CharConstant

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int = value.hashCode()
}

class FloatConstant(val value: Float, type: Type) : Constant(value.toString(), type) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FloatConstant

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int = value.hashCode()
}

class DoubleConstant(val value: Double, type: Type) : Constant(value.toString(), type) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DoubleConstant

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int = value.hashCode()
}

class StringConstant(val value: String, type: Type) : Constant("\"$value\"", type) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as StringConstant

        if (value != other.value) return false

        return true
    }

    override fun hashCode(): Int = value.hashCode()
}

class MethodConstant(val method: Method, type: Type) : Constant(method.name, type) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MethodConstant

        if (method != other.method) return false

        return true
    }

    override fun hashCode(): Int = method.hashCode()
}

class ClassConstant(`class`: Type) : Constant("${`class`.name}.class", `class`)
class NullConstant(type: Type) : Constant("null", type)