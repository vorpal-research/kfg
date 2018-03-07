package org.jetbrains.research.kfg.type

class VoidType : Type {
    companion object {
        val instance = VoidType()
    }

    override fun getName() = "void"
    override fun isPrimary() = false
    override fun isVoid() = true
    override fun getAsmDesc() = "V"
}