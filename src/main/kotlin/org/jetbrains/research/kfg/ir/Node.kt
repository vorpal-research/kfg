package org.jetbrains.research.kfg.ir

import jdk.internal.org.objectweb.asm.Opcodes
import org.jetbrains.research.kfg.type.parseDesc
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.TypeAnnotationNode

abstract class Node {
    val name: String
    var builded = false
    var modifiers: Int
    val visibleAnnotations = mutableListOf<Annotation>()
    val invisibleAnnotations = mutableListOf<Annotation>()
    val visibleTypeAnnotations = mutableListOf<TypeAnnotation>()
    val invisibleTypeAnnotations = mutableListOf<TypeAnnotation>()

    constructor(name: String) : this(name, 0)

    constructor(name: String, modifiers: Int) {
        this.name = name
        this.modifiers = modifiers
    }

    constructor(name: String, modifiers: Int,
                visibleAnnotations: List<AnnotationNode>,
                invisibleAnnotations: List<AnnotationNode>,
                visibleTypeAnnotations: List<TypeAnnotationNode>,
                invisibleTypeAnnotations: List<TypeAnnotationNode>) {
        this.name = name
        this.modifiers = modifiers
    }

    fun addVisibleAnnotations(visibleAnnotations: List<AnnotationNode>?) {
        if (visibleAnnotations != null) {
            this.visibleAnnotations.addAll(visibleAnnotations.map { Annotation(parseDesc(it.desc)) })
        }
    }

    fun addInvisibleAnnotations(invisibleAnnotations: List<AnnotationNode>?) {
        if (invisibleAnnotations != null) {
            this.invisibleAnnotations.addAll(invisibleAnnotations.map { Annotation(parseDesc(it.desc)) })
        }
    }

    fun addVisibleTypeAnnotations(visibleAnnotations: List<TypeAnnotationNode>?) {
        if (visibleAnnotations != null) {
            this.visibleTypeAnnotations.addAll(visibleAnnotations.map { TypeAnnotation(parseDesc(it.desc), it.typeRef) })
        }
    }

    fun addInvisibleTypeAnnotations(invisibleAnnotations: List<TypeAnnotationNode>?) {
        if (invisibleAnnotations != null) {
            this.invisibleTypeAnnotations.addAll(invisibleAnnotations.map { TypeAnnotation(parseDesc(it.desc), it.typeRef) })
        }
    }


    fun isPublic() = (modifiers and Opcodes.ACC_PUBLIC) == Opcodes.ACC_PUBLIC
    fun isPrivate() = (modifiers and Opcodes.ACC_PRIVATE) == Opcodes.ACC_PRIVATE
    fun isProtected() = (modifiers and Opcodes.ACC_PROTECTED) == Opcodes.ACC_PROTECTED
    fun isStatic() = (modifiers and Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC
    fun isFinal() = (modifiers and Opcodes.ACC_FINAL) == Opcodes.ACC_FINAL
    fun isSuper() = (modifiers and Opcodes.ACC_SUPER) == Opcodes.ACC_SUPER
    fun isSynchronized() = (modifiers and Opcodes.ACC_SYNCHRONIZED) == Opcodes.ACC_SYNCHRONIZED
    fun isVolatile() = (modifiers and Opcodes.ACC_VOLATILE) == Opcodes.ACC_VOLATILE
    fun isBridge() = (modifiers and Opcodes.ACC_BRIDGE) == Opcodes.ACC_BRIDGE
    fun isVarargs() = (modifiers and Opcodes.ACC_VARARGS) == Opcodes.ACC_VARARGS
    fun isTransient() = (modifiers and Opcodes.ACC_TRANSIENT) == Opcodes.ACC_TRANSIENT
    fun isNative() = (modifiers and Opcodes.ACC_NATIVE) == Opcodes.ACC_NATIVE
    fun isInterface() = (modifiers and Opcodes.ACC_INTERFACE) == Opcodes.ACC_INTERFACE
    fun isAbstract() = (modifiers and Opcodes.ACC_ABSTRACT) == Opcodes.ACC_ABSTRACT
    fun isStrict() = (modifiers and Opcodes.ACC_STRICT) == Opcodes.ACC_STRICT
    fun isSynthetic() = (modifiers and Opcodes.ACC_SYNTHETIC) == Opcodes.ACC_SYNTHETIC
    fun isAnnotation() = (modifiers and Opcodes.ACC_ANNOTATION) == Opcodes.ACC_ANNOTATION
    fun isEnum() = (modifiers and Opcodes.ACC_ENUM) == Opcodes.ACC_ENUM
}