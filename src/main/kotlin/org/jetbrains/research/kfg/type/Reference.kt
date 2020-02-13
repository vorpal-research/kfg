package org.jetbrains.research.kfg.type

import com.abdullin.kthelper.defaultHashCode
import org.jetbrains.research.kfg.InvalidCallError
import org.jetbrains.research.kfg.ir.Class

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
}

object NullType : Reference {
    override val name = "null"

    override fun toString() = name
    override val asmDesc get() = throw InvalidCallError("Called getAsmDesc on NullType")

    override fun hashCode() = defaultHashCode(name)
    override fun equals(other: Any?): Boolean = this === other
}
