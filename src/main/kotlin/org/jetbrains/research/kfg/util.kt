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

fun ClassNode.printBytecode(): String {
    val sb = StringBuilder()
    sb.appendln("Class ${this.name}")
    val methods = this.methods as MutableList<MethodNode>
    for (mn in methods) {
        sb.appendln(mn.printBytecode())
    }
    return sb.toString()
}

fun MethodNode.printBytecode(): String {
    val sb = StringBuilder()
    sb.appendln(this.name)
    for (insn in this.instructions) {
        val ain = insn as AbstractInsnNode
        sb.append(ain.printBytecode())
    }
    return sb.toString()
}

fun AbstractInsnNode.printBytecode(): String {
    this.accept(mp)
    val sw = StringWriter()
    printer.print(PrintWriter(sw))
    printer.getText().clear()
    return sw.toString()
}