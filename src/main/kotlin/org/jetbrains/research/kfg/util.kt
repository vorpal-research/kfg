package org.jetbrains.research.kfg

import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceMethodVisitor
import java.io.PrintWriter
import java.io.StringWriter

private val printer = Textifier()
private val mp = TraceMethodVisitor(printer)

fun printBytecode(cn: ClassNode): String {
    val sb = StringBuilder()
    sb.appendln("Class ${cn.name}")
    val methods = cn.methods as MutableList<MethodNode>
    for (mn in methods) {
        sb.appendln(printBytecode(mn))
    }
    return sb.toString()
}

fun printBytecode(mn: MethodNode): String {
    val sb = StringBuilder()
    sb.appendln(mn.name)
    for (insn in mn.instructions) {
        sb.append(printAbstractInsn(insn as AbstractInsnNode))
    }
    return sb.toString()
}

fun printAbstractInsn(insn: AbstractInsnNode): String {
    insn.accept(mp)
    val sw = StringWriter()
    printer.print(PrintWriter(sw))
    printer.getText().clear()
    return sw.toString()
}