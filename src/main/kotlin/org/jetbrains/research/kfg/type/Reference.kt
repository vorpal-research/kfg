package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.InvalidCallException
import org.jetbrains.research.kfg.util.defaultHasCode
import org.jetbrains.research.kfg.ir.Class

interface Reference : Type {
    override fun isPrimary() = false
    override fun isReference() = true
}

class ClassType(val `class`: Class) : Reference {
    override val name = `class`.name

    override fun toString() = name
    override fun getAsmDesc() = "L${`class`.getFullname()};"

    override fun hashCode() = defaultHasCode(`class`)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (this.javaClass != other?.javaClass) return false
        other as ClassType
        return this.`class` == other.`class`
    }
}

class ArrayType(val component: Type) : Reference {
    override val name = "$component[]"
    override fun toString() = name
    override fun getAsmDesc() = "[${component.getAsmDesc()}"

    override fun hashCode() = defaultHasCode(component)
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
    override fun getAsmDesc() = throw InvalidCallException("Called getAsmDesc on NullType")

    override fun hashCode() = defaultHasCode(name)
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return this.javaClass != other?.javaClass
    }
}
