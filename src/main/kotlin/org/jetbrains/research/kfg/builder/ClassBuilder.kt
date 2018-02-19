package org.jetbrains.research.kfg.builder

import org.jetbrains.research.kfg.InvalidOperandException
import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.ClassManager
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.parseDesc
import org.jetbrains.research.kfg.type.parseMethodDesc
import org.jetbrains.research.kfg.value.Field
import org.jetbrains.research.kfg.value.ValueFactory
import org.objectweb.asm.*
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

class ClassBuilder(val cn: ClassNode) : ClassVisitor(Opcodes.ASM5) {
    val currentClass: Class
    val CM = ClassManager.instance
    val VF = ValueFactory.instance

    init {
        currentClass = CM.createOrGet(cn.name)
        if (cn.superName != null) currentClass.superClass = CM.createOrGet(cn.superName)
    }

    override fun visitField(access: Int, name: String, desc: String, signature: String?, value: Any?): FieldVisitor? {
        val type = parseDesc(desc)
        val `val` = when (value) {
            Int -> VF.getIntConstant(value as Int)
            Float -> VF.getFloatConstant(value as Float)
            Long -> VF.getLongConstant(value as Long)
            Double -> VF.getDoubleConstant(value as Double)
            String -> VF.getStringConstant(value as String)
            else -> null
        }
        val field =
                if (`val` != null) VF.getField(name, currentClass, type, `val`)
                else VF.getField(name, currentClass, type)
        currentClass.fields.add(field as Field)
        return null
    }

    override fun visitMethod(access: Int, name: String, desc: String,
                    signature: String?, exceptions: Array<String>?): MethodVisitor? {
        super.visitMethod(access, name, desc, signature, exceptions)
        val methodType = parseMethodDesc(desc)
        val method = Method(name, currentClass, access, methodType.first, methodType.second)
        return MethodBuilder(method, desc, exceptions?.map { it.toString() }?.toTypedArray() ?: arrayOf())
    }
}