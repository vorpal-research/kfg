package org.jetbrains.research.kfg.ir.value

import org.jetbrains.research.kfg.TF
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.Type

abstract class Constant(name: String, type: Type) : Value(name, type)

class BoolConstant(val value: Boolean) : Constant(value.toString(), TF.getBoolType()) {
    override fun hashCode() = value.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as BoolConstant
        return this.value == other.value
    }
}

class ByteConstant(val value: Byte) : Constant(value.toString(), TF.getByteType()) {
    override fun hashCode() = value.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as ByteConstant
        return this.value == other.value
    }
}

class ShortConstant(val value: Short) : Constant(value.toString(), TF.getShortType()) {
    override fun hashCode() = value.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as ShortConstant
        return this.value == other.value
    }
}

class IntConstant(val value: Int) : Constant(value.toString(), TF.getIntType()) {
    override fun hashCode() = value.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as IntConstant
        return this.value == other.value
    }
}

class LongConstant(val value: Long) : Constant(value.toString(), TF.getLongType()) {
    override fun hashCode() = value.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as LongConstant
        return this.value == other.value
    }
}

class CharConstant(val value: Char) : Constant(value.toString(), TF.getCharType()) {
    override fun hashCode() = value.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as CharConstant
        return this.value == other.value
    }
}

class FloatConstant(val value: Float) : Constant(value.toString(), TF.getFloatType()) {
    override fun hashCode() = value.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as FloatConstant
        return this.value == other.value
    }
}

class DoubleConstant(val value: Double) : Constant(value.toString(), TF.getDoubleType()) {
    override fun hashCode() = value.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as DoubleConstant
        return this.value == other.value
    }
}

class StringConstant(val value: String) : Constant("\"$value\"", TF.getRefType("java/lang/String")) {
    override fun hashCode() = value.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as StringConstant
        return this.value == other.value
    }
}

class MethodConstant(val method: Method) : Constant(method.name, TF.getRefType("java/lang/invoke/MethodHandle")) {
    override fun hashCode() = method.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false
        other as MethodConstant
        return this.method == other.method
    }
}

class ClassConstant(`class`: Type) : Constant("${`class`.name}.class", `class`)
object NullConstant : Constant("null", TF.getNullType())