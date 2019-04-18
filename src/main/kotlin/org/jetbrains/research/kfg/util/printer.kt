package org.jetbrains.research.kfg.util

import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import org.objectweb.asm.tree.TryCatchBlockNode
import org.objectweb.asm.util.Textifier
import org.objectweb.asm.util.TraceMethodVisitor
import java.io.PrintWriter
import java.io.StringWriter

private val printer = Textifier()
private val mp = TraceMethodVisitor(printer)

fun ClassNode.print() = buildString {
    appendln("Class ${this@print.name}")
    val methods = @Suppress("UNCHECKED_CAST") (this@print.methods as MutableList<MethodNode>)
    for (mn in methods) {
        appendln(mn.print())
    }
}

fun MethodNode.print() = buildString {
    appendln(this@print.name)
    for (insn in this@print.instructions) {
        val ain = insn as AbstractInsnNode
        append(ain.print())
    }
    for (insn in this@print.tryCatchBlocks) {
        val ain = insn as TryCatchBlockNode
        append(ain.print())
    }
}

fun AbstractInsnNode.print(): String {
    this.accept(mp)
    val sw = StringWriter()
    printer.print(PrintWriter(sw))
    printer.getText().clear()
    return sw.toString()
}

fun TryCatchBlockNode.print() = buildString {
    append("${start.print().dropLast(1)} ")
    append("${end.print().dropLast(1)} ")
    append("${handler.print().dropLast(1)} ")
    appendln(type ?: "java/lang/Throwable")
}