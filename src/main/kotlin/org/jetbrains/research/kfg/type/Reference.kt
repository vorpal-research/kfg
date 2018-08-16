package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.InvalidCallError
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.util.simpleHash

interface Reference : Type {
    override fun isPrimary() = false
    override fun isReference() = true
}

open class ClassType(val `class`: Class) : Reference {
    override val name = `class`.fullname

    override fun toString() = name
    override fun getAsmDesc() = "L${`class`.fullname};"

    override fun hashCode() = simpleHash(`class`)
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
    override fun getAsmDesc() = "[${component.getAsmDesc()}"

    override fun hashCode() = simpleHash(component)
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
    override fun getAsmDesc() = throw InvalidCallError("Called getAsmDesc on NullType")

    override fun hashCode() = simpleHash(name)
    override fun equals(other: Any?): Boolean = this === other
}
