package org.vorpal.research.kfg.ir

import org.objectweb.asm.Opcodes
import org.vorpal.research.kfg.ClassManager

@JvmInline
value class Modifiers(val value: Int) {
    val isPublic: Boolean
        get() = (value and Opcodes.ACC_PUBLIC) == Opcodes.ACC_PUBLIC

    fun setPublic(value: Boolean) = Modifiers(
        when {
            value -> this.value or Opcodes.ACC_PUBLIC
            else -> this.value and (Opcodes.ACC_PUBLIC).inv()
        }
    )

    val isPrivate: Boolean
        get() = (value and Opcodes.ACC_PRIVATE) == Opcodes.ACC_PRIVATE

    fun setPrivate(value: Boolean) = Modifiers(
        when {
            value -> this.value or Opcodes.ACC_PRIVATE
            else -> this.value and (Opcodes.ACC_PRIVATE).inv()
        }
    )

    val isProtected: Boolean
        get() = (value and Opcodes.ACC_PROTECTED) == Opcodes.ACC_PROTECTED

    fun setProtected(value: Boolean) = Modifiers(
        when {
            value -> this.value or Opcodes.ACC_PROTECTED
            else -> this.value and (Opcodes.ACC_PROTECTED).inv()
        }
    )

    val isStatic: Boolean
        get() = (value and Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC

    fun setStatic(value: Boolean) = Modifiers(
        when {
            value -> this.value or Opcodes.ACC_STATIC
            else -> this.value and (Opcodes.ACC_STATIC).inv()
        }
    )

    val isFinal: Boolean
        get() = (value and Opcodes.ACC_FINAL) == Opcodes.ACC_FINAL

    fun setFinal(value: Boolean) = Modifiers(
        when {
            value -> this.value or Opcodes.ACC_FINAL
            else -> this.value and (Opcodes.ACC_FINAL).inv()
        }
    )

    val isSuper: Boolean
        get() = (value and Opcodes.ACC_SUPER) == Opcodes.ACC_SUPER

    fun setSuper(value: Boolean) = Modifiers(
        when {
            value -> this.value or Opcodes.ACC_SUPER
            else -> this.value and (Opcodes.ACC_SUPER).inv()
        }
    )

    val isSynchronized: Boolean
        get() = (value and Opcodes.ACC_SYNCHRONIZED) == Opcodes.ACC_SYNCHRONIZED

    fun setSynchronized(value: Boolean) = Modifiers(
        when {
            value -> this.value or Opcodes.ACC_SYNCHRONIZED
            else -> this.value and (Opcodes.ACC_SYNCHRONIZED).inv()
        }
    )

    val isVolatile: Boolean
        get() = (value and Opcodes.ACC_VOLATILE) == Opcodes.ACC_VOLATILE

    fun setVolatile(value: Boolean) = Modifiers(
        when {
            value -> this.value or Opcodes.ACC_VOLATILE
            else -> this.value and (Opcodes.ACC_VOLATILE).inv()
        }
    )

    val isBridge: Boolean
        get() = (value and Opcodes.ACC_BRIDGE) == Opcodes.ACC_BRIDGE

    fun setBridge(value: Boolean) = Modifiers(
        when {
            value -> this.value or Opcodes.ACC_BRIDGE
            else -> this.value and (Opcodes.ACC_BRIDGE).inv()
        }
    )

    val isVarargs: Boolean
        get() = (value and Opcodes.ACC_VARARGS) == Opcodes.ACC_VARARGS

    fun setVarargs(value: Boolean) = Modifiers(
        when {
            value -> this.value or Opcodes.ACC_VARARGS
            else -> this.value and (Opcodes.ACC_VARARGS).inv()
        }
    )

    val isTransient: Boolean
        get() = (value and Opcodes.ACC_TRANSIENT) == Opcodes.ACC_TRANSIENT

    fun setTransient(value: Boolean) = Modifiers(
        when {
            value -> this.value or Opcodes.ACC_TRANSIENT
            else -> this.value and (Opcodes.ACC_TRANSIENT).inv()
        }
    )

    val isNative: Boolean
        get() = (value and Opcodes.ACC_NATIVE) == Opcodes.ACC_NATIVE

    fun setNative(value: Boolean) = Modifiers(
        when {
            value -> this.value or Opcodes.ACC_NATIVE
            else -> this.value and (Opcodes.ACC_NATIVE).inv()
        }
    )

    val isInterface: Boolean
        get() = (value and Opcodes.ACC_INTERFACE) == Opcodes.ACC_INTERFACE

    fun setInterface(value: Boolean) = Modifiers(
        when {
            value -> this.value or Opcodes.ACC_INTERFACE
            else -> this.value and (Opcodes.ACC_INTERFACE).inv()
        }
    )

    val isAbstract: Boolean
        get() = (value and Opcodes.ACC_ABSTRACT) == Opcodes.ACC_ABSTRACT

    fun setAbstract(value: Boolean) = Modifiers(
        when {
            value -> this.value or Opcodes.ACC_ABSTRACT
            else -> this.value and (Opcodes.ACC_ABSTRACT).inv()
        }
    )

    val isStrict: Boolean
        get() = (value and Opcodes.ACC_STRICT) == Opcodes.ACC_STRICT

    fun setStrict(value: Boolean) = Modifiers(
        when {
            value -> this.value or Opcodes.ACC_STRICT
            else -> this.value and (Opcodes.ACC_STRICT).inv()
        }
    )

    val isSynthetic: Boolean
        get() = (value and Opcodes.ACC_SYNTHETIC) == Opcodes.ACC_SYNTHETIC

    fun setSynthetic(value: Boolean) = Modifiers(
        when {
            value -> this.value or Opcodes.ACC_SYNTHETIC
            else -> this.value and (Opcodes.ACC_SYNTHETIC).inv()
        }
    )

    val isAnnotation: Boolean
        get() = (value and Opcodes.ACC_ANNOTATION) == Opcodes.ACC_ANNOTATION

    fun setAnnotation(value: Boolean) = Modifiers(
        when {
            value -> this.value or Opcodes.ACC_ANNOTATION
            else -> this.value and (Opcodes.ACC_ANNOTATION).inv()
        }
    )

    val isEnum: Boolean
        get() = (value and Opcodes.ACC_ENUM) == Opcodes.ACC_ENUM

    fun setEnum(value: Boolean) = Modifiers(
        when {
            value -> this.value or Opcodes.ACC_ENUM
            else -> this.value and (Opcodes.ACC_ENUM).inv()
        }
    )
}

abstract class Node(
    val cm: ClassManager,
    val name: String,
    modifiers: Modifiers,
) {
    protected abstract val innerAnnotations: MutableSet<Annotation>
    protected abstract val innerTypeAnnotations: MutableSet<TypeAnnotation>
    var modifiers: Modifiers = modifiers
        protected set

    abstract val asmDesc: String
    val annotations: Set<Annotation> get() = innerAnnotations
    val typeAnnotations: Set<TypeAnnotation> get() = innerTypeAnnotations

    var isPublic: Boolean
        get() = modifiers.isPublic
        set(value) {
            modifiers = modifiers.setPublic(value)
        }

    var isPrivate: Boolean
        get() = modifiers.isPrivate
        set(value) {
            modifiers = modifiers.setPrivate(value)
        }

    var isProtected: Boolean
        get() = modifiers.isProtected
        set(value) {
            modifiers = modifiers.setProtected(value)
        }

    var isStatic: Boolean
        get() = modifiers.isStatic
        set(value) {
            modifiers = modifiers.setStatic(value)
        }

    var isFinal: Boolean
        get() = modifiers.isFinal
        set(value) {
            modifiers = modifiers.setFinal(value)
        }

    var isSuper: Boolean
        get() = modifiers.isSuper
        set(value) {
            modifiers = modifiers.setSuper(value)
        }

    var isSynchronized: Boolean
        get() = modifiers.isSynchronized
        set(value) {
            modifiers = modifiers.setSynchronized(value)
        }

    var isVolatile: Boolean
        get() = modifiers.isVolatile
        set(value) {
            modifiers = modifiers.setVolatile(value)
        }

    var isBridge: Boolean
        get() = modifiers.isBridge
        set(value) {
            modifiers = modifiers.setBridge(value)
        }

    var isVarargs: Boolean
        get() = modifiers.isVarargs
        set(value) {
            modifiers = modifiers.setVarargs(value)
        }

    var isTransient: Boolean
        get() = modifiers.isTransient
        set(value) {
            modifiers = modifiers.setTransient(value)
        }

    var isNative: Boolean
        get() = modifiers.isNative
        set(value) {
            modifiers = modifiers.setNative(value)
        }

    var isInterface: Boolean
        get() = modifiers.isInterface
        set(value) {
            modifiers = modifiers.setInterface(value)
        }

    var isAbstract: Boolean
        get() = modifiers.isAbstract
        set(value) {
            modifiers = modifiers.setAbstract(value)
        }

    var isStrict: Boolean
        get() = modifiers.isStrict
        set(value) {
            modifiers = modifiers.setStrict(value)
        }

    var isSynthetic: Boolean
        get() = modifiers.isSynthetic
        set(value) {
            modifiers = modifiers.setSynthetic(value)
        }

    var isAnnotation: Boolean
        get() = modifiers.isAnnotation
        set(value) {
            modifiers = modifiers.setAnnotation(value)
        }

    var isEnum: Boolean
        get() = modifiers.isEnum
        set(value) {
            modifiers = modifiers.setEnum(value)
        }

    protected open fun addAnnotation(annotation: Annotation) {
        this.innerAnnotations += annotation
    }
    protected open fun addTypeAnnotation(annotation: TypeAnnotation) {
        this.innerTypeAnnotations += annotation
    }

    protected open fun removeAnnotation(annotation: Annotation) {
        this.innerAnnotations -= annotation
    }
    protected open fun removeTypeAnnotation(annotation: TypeAnnotation) {
        this.innerTypeAnnotations -= annotation
    }
}
