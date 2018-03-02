package org.jetbrains.research.kfg.type

import org.jetbrains.research.kfg.ir.Class

interface Reference : Type {
    override fun isPrimitive() = false
}

class ClassType(val `class`: Class) : Reference {
    override fun getName() = `class`.name
    override fun toString() = getName()
}

class ArrayType(val component: Type) : Reference {
    override fun getName() = "$component[]"
    override fun toString() = getName()
}

class NullType : Reference {
    companion object {
        val instance = NullType()
    }

    override fun getName() = "null"
    override fun toString() = getName()
}
