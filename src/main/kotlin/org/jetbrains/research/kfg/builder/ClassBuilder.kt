package org.jetbrains.research.kfg.builder

import org.jetbrains.research.kfg.ir.Class
import org.jetbrains.research.kfg.ir.ClassManager
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.type.parseDesc
import org.jetbrains.research.kfg.type.parseMethodDesc
import org.jetbrains.research.kfg.ir.value.Field
import org.jetbrains.research.kfg.ir.value.ValueFactory
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

class ClassBuilder(val cn: ClassNode) {
    val currentClass: Class
    val CM = ClassManager.instance
    val VF = ValueFactory.instance

    init {
        currentClass = CM.createOrGet(cn.name)
    }

    private fun visitField(fn: FieldNode) {
        val type = parseDesc(fn.desc)
        val `val` = when (fn.value) {
            Int -> VF.getIntConstant(fn.value as Int)
            Float -> VF.getFloatConstant(fn.value as Float)
            Long -> VF.getLongConstant(fn.value as Long)
            Double -> VF.getDoubleConstant(fn.value as Double)
            String -> VF.getStringConstant(fn.value as String)
            else -> null
        }
        val field =
                if (`val` != null) VF.getField(fn.name, currentClass, type, `val`)
                else VF.getField(fn.name, currentClass, type)
        currentClass.fields.add(field as Field)
    }

    private fun visitMethod(mn: MethodNode) {
        val methodType = parseMethodDesc(mn.desc)
        val method = Method(mn.name, currentClass, mn.access, methodType.first, methodType.second)
        MethodBuilder(method, mn).convert()
    }

    fun doit() {
        if (cn.superName != null) currentClass.superClass = CM.createOrGet(cn.superName)
        cn.fields.forEach { visitField(it as FieldNode) }
        cn.methods.forEach { visitMethod(it as MethodNode) }
    }
}