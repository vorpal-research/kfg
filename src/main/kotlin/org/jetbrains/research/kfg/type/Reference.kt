package org.jetbrains.research.kfg.type

interface Reference : Type {
    override fun isPrimitive() = false
}

class ClassType(val className: String) : Reference {
    override fun getName() = className
    override fun toString() = getName()
}

class ArrayType(val component: Type) : Reference {
    override fun getName() = "$component[]"
    override fun toString() = getName()
}

class InterfaceType(val interfaceName: String) : Reference {
    override fun getName() = interfaceName
    override fun toString() = getName()
}

class NullType : Reference {
    companion object {
        val instance = NullType()
    }

    override fun getName() = "null"
    override fun toString() = getName()
}
