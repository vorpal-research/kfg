package org.jetbrains.research.kfg.util

import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

fun ClassNode.methods(): List<MethodNode> {
    val list = mutableListOf<MethodNode>()
    for (mn in this.methods)
        list += mn as MethodNode
    return list
}

fun MethodNode.instructions(): List<AbstractInsnNode> {
    val list = mutableListOf<AbstractInsnNode>()
    var current: AbstractInsnNode? = instructions.first
    while (current != null) {
        list += current
        current = current.next
    }
    return list
}