package org.vorpal.research.kfg.type

import org.vorpal.research.kfg.UnsupportedOperationException
import org.vorpal.research.kfg.ir.Class
import org.vorpal.research.kfg.ir.ConcreteClass

interface Reference : Type {
    override val bitSize: Int
        get() = Type.WORD

    override val isPrimary get() = false
    override val isReference get() = true
}

open class ClassType(val klass: Class) : Reference {
    override val name = klass.fullName

    override fun toString() = name
    override val asmDesc get() = "L${klass.fullName};"

    override fun hashCode() = klass.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ClassType
        return this.klass == other.klass
    }

    override val isConcrete: Boolean
        get() = klass is ConcreteClass
    override fun isSubtypeOf(other: Type): Boolean = when (other) {
        is ClassType -> this.klass.isInheritorOf(other.klass)
        else -> false
    }
}

open class ArrayType(val component: Type) : Reference {
    override val name = "$component[]"
    override fun toString() = name
    override val asmDesc get() = "[${component.asmDesc}"

    override fun hashCode() = component.hashCode()
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (this.javaClass != other?.javaClass) return false
        other as ArrayType
        return this.component == other.component
    }

    override val isConcrete: Boolean
        get() = component.isConcrete

    override fun isSubtypeOf(other: Type): Boolean = when (other) {
        this -> true
        is ArrayType -> when {
            this.component.isReference && other.component.isReference -> this.component.isSubtypeOf(other.component)
            else -> false
        }
        is ClassType -> when (other.klass.fullName) {
            "java/lang/Object" -> true
            "java/lang/Cloneable" -> true
            "java/io/Serializable" -> true
            else -> false
        }
        else -> false
    }
}

object NullType : Reference {
    override val name = "null"

    override fun toString() = name
    override val asmDesc get() = throw UnsupportedOperationException("Called getAsmDesc on NullType")

    override fun hashCode() = name.hashCode()
    override fun equals(other: Any?): Boolean = this === other

    override val isConcrete: Boolean
        get() = true
    override fun isSubtypeOf(other: Type) = true
}
