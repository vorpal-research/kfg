package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.VF
import org.jetbrains.research.kfg.type.parseMethodDesc
import org.jetbrains.research.kfg.ir.value.FieldValue
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.parseDesc
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.tree.MethodNode

class Class : Node {
    val packageName: String
    val fields = mutableMapOf<String, Field>()
    val interfaces = mutableListOf<Class>()
    val methods = mutableMapOf<String, Method>()
    var superClass: Class?

    constructor(fullName: String) : this(fullName, null)
    constructor(name: String, packageName: String) : this(name, packageName, null)

    constructor(fullName: String, superClass: Class?, modifiers: Int = 0) : super(fullName.substringAfterLast('.'), modifiers) {
        this.packageName = fullName.substringBeforeLast('.')
        this.superClass = superClass
        this.modifiers = modifiers
    }

    constructor(name: String, packageName: String, superClass: Class?, modifiers: Int = 0) : super(name, modifiers) {
        this.packageName = packageName
        this.superClass = superClass
        this.modifiers = modifiers
    }

    fun getFullname() = "$packageName.$name"
    private fun getMethodByDesc(desc: String) = methods[desc]

    fun getField(fn: FieldNode) = fields.getOrPut(fn.name, { Field(fn, this) })
    fun getField(name: String, type: Type, default: Value? = null) = fields.getOrPut(name, { Field(name, this, type, default) })

    fun getMethod(mn: MethodNode) = getMethod(mn.name, mn.access, mn.desc)

    fun getMethod(name: String, modifiers: Int, desc: String): Method {
        val method = getMethod(name, desc)
        method.modifiers = modifiers
        return method
    }

    fun getMethod(name: String, desc: String): Method {
        val pr = parseMethodDesc(desc)
        val fullDesc = createMethodDesc(name, this, pr.first, pr.second)
        return methods.getOrPut(fullDesc, { Method(name, this, desc) })
    }
}