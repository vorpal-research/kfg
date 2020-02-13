package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.ClassManager
import org.jetbrains.research.kfg.type.parseDesc
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode

abstract class Node(val cm: ClassManager, val name: String, val modifiers: Int) {
    val visibleAnnotations = mutableListOf<Annotation>()
    val invisibleAnnotations = mutableListOf<Annotation>()

    abstract val asmDesc: String

    fun addVisibleAnnotations(visibleAnnotations: List<AnnotationNode>?) {
        if (visibleAnnotations != null) {
            this.visibleAnnotations.addAll(visibleAnnotations.map { Annotation(parseDesc(cm.type, it.desc)) })
        }
    }

    fun addInvisibleAnnotations(invisibleAnnotations: List<AnnotationNode>?) {
        if (invisibleAnnotations != null) {
            this.invisibleAnnotations.addAll(invisibleAnnotations.map { Annotation(parseDesc(cm.type, it.desc)) })
        }
    }

    val isPublic: Boolean
        get() = (modifiers and Opcodes.ACC_PUBLIC) == Opcodes.ACC_PUBLIC

    val isPrivate: Boolean
        get() = (modifiers and Opcodes.ACC_PRIVATE) == Opcodes.ACC_PRIVATE

    val isProtected: Boolean
        get() = (modifiers and Opcodes.ACC_PROTECTED) == Opcodes.ACC_PROTECTED

    val isStatic: Boolean
        get() = (modifiers and Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC

    val isFinal: Boolean
        get() = (modifiers and Opcodes.ACC_FINAL) == Opcodes.ACC_FINAL

    val isSuper: Boolean
        get() = (modifiers and Opcodes.ACC_SUPER) == Opcodes.ACC_SUPER

    val isSynchronized: Boolean
        get() = (modifiers and Opcodes.ACC_SYNCHRONIZED) == Opcodes.ACC_SYNCHRONIZED

    val isVolatile: Boolean
        get() = (modifiers and Opcodes.ACC_VOLATILE) == Opcodes.ACC_VOLATILE

    val isBridge: Boolean
        get() = (modifiers and Opcodes.ACC_BRIDGE) == Opcodes.ACC_BRIDGE

    val isVarargs: Boolean
        get() = (modifiers and Opcodes.ACC_VARARGS) == Opcodes.ACC_VARARGS

    val isTransient: Boolean
        get() = (modifiers and Opcodes.ACC_TRANSIENT) == Opcodes.ACC_TRANSIENT

    val isNative: Boolean
        get() = (modifiers and Opcodes.ACC_NATIVE) == Opcodes.ACC_NATIVE

    val isInterface: Boolean
        get() = (modifiers and Opcodes.ACC_INTERFACE) == Opcodes.ACC_INTERFACE

    val isAbstract: Boolean
        get() = (modifiers and Opcodes.ACC_ABSTRACT) == Opcodes.ACC_ABSTRACT

    val isStrict: Boolean
        get() = (modifiers and Opcodes.ACC_STRICT) == Opcodes.ACC_STRICT

    val isSynthetic: Boolean
        get() = (modifiers and Opcodes.ACC_SYNTHETIC) == Opcodes.ACC_SYNTHETIC

    val isAnnotation: Boolean
        get() = (modifiers and Opcodes.ACC_ANNOTATION) == Opcodes.ACC_ANNOTATION

    val isEnum: Boolean
        get() = (modifiers and Opcodes.ACC_ENUM) == Opcodes.ACC_ENUM
}