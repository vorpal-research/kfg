package org.jetbrains.research.kfg.type

import org.jetbrains.research.kthelper.defaultHashCode
import org.jetbrains.research.kfg.InvalidCallError
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.ConcreteClass

interface Reference : Type {
    override val bitsize: Int
        get() = Type.WORD

    override val isPrimary get() = false
    override val isReference get() = true
}

open class ClassType(val `class`: Class) : Reference {
    override val name = `class`.fullname

    override fun toString() = name
    override val asmDesc get() = "L${`class`.fullname};"

    override fun hashCode() = defaultHashCode(`class`)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ClassType
        return this.`class` == other.`class`
    }

    override val isConcrete: Boolean
        get() = `class` is ConcreteClass
    override fun isSubtypeOf(other: Type): Boolean = when (other) {
        is ClassType -> this.`class`.isInheritorOf(other.`class`)
        else -> false
    }
}

open class ArrayType(val component: Type) : Reference {
    override val name = "$component[]"
    override fun toString() = name
    override val asmDesc get() = "[${component.asmDesc}"

    override fun hashCode() = defaultHashCode(component)
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
        is ClassType -> when (other.`class`.fullname) {
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
    override val asmDesc get() = throw InvalidCallError("Called getAsmDesc on NullType")

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?): Boolean = this === other

    override val isConcrete: Boolean
        get() = true
    override fun isSubtypeOf(other: Type) = true
}
