package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.InvalidCallException
import org.jetbrains.research.kfg.ir.Class

interface Reference : Type {
    override fun isPrimary() = false
    override fun isReference() = true
}

class ClassType(val `class`: Class) : Reference {
    override fun getName() = `class`.name
    override fun toString() = getName()
    override fun getAsmDesc() = "L${getName()};"
}

class ArrayType(val component: Type) : Reference {
    override fun getName() = "$component[]"
    override fun toString() = getName()
    override fun getAsmDesc() = "[${component.getAsmDesc()}"
}

class NullType : Reference {
    companion object {
        val instance = NullType()
    }

    override fun getName() = "null"
    override fun toString() = getName()
    override fun getAsmDesc() = throw InvalidCallException("Called getAsmDesc on NullType")
}
