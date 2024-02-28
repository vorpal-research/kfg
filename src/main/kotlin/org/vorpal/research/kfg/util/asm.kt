package org.vorpal.research.kfg.util

import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.ir.MethodDescriptor
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kthelper.assert.unreachable
import org.objectweb.asm.Type as AsmType

sealed interface TypeHolder {
    fun toAsmType(): AsmType
}

data class DefaultTypeHolder(val type: Type) : TypeHolder {
    override fun toAsmType(): AsmType = org.objectweb.asm.Type.getType(type.asmDesc)
}
data class MethodDescriptorHolder(val desc: MethodDescriptor) : TypeHolder {
    override fun toAsmType(): AsmType = org.objectweb.asm.Type.getType(desc.asmDesc)
}

val Type.asHolder: TypeHolder get() = DefaultTypeHolder(this)

fun AsmType.getAsKfgType(cm: ClassManager): TypeHolder = when (this.sort) {
    AsmType.VOID -> cm.type.voidType.asHolder
    AsmType.BOOLEAN -> cm.type.boolType.asHolder
    AsmType.CHAR -> cm.type.charType.asHolder
    AsmType.BYTE -> cm.type.byteType.asHolder
    AsmType.SHORT -> cm.type.shortType.asHolder
    AsmType.INT -> cm.type.intType.asHolder
    AsmType.FLOAT -> cm.type.floatType.asHolder
    AsmType.LONG -> cm.type.longType.asHolder
    AsmType.DOUBLE -> cm.type.doubleType.asHolder
    AsmType.ARRAY -> (this.elementType.getAsKfgType(cm) as DefaultTypeHolder).type.asArray.asHolder
    AsmType.OBJECT -> cm[this.className.replace('.', '/')].asType.asHolder
    AsmType.METHOD -> MethodDescriptorHolder(
        MethodDescriptor(
            this.argumentTypes.map { (it.getAsKfgType(cm) as DefaultTypeHolder).type },
            (this.returnType.getAsKfgType(cm) as DefaultTypeHolder).type
        )
    )

    else -> unreachable("Unknown type: $this")
}
