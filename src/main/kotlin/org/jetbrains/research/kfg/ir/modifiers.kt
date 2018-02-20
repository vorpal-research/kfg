package org.jetbrains.research.kfg.ir

import jdk.internal.org.objectweb.asm.Opcodes

fun isPublic(modifiers: Int) = (modifiers and Opcodes.ACC_PUBLIC) == Opcodes.ACC_PUBLIC
fun isPrivate(modifiers: Int) = (modifiers and Opcodes.ACC_PRIVATE) == Opcodes.ACC_PRIVATE
fun isProtected(modifiers: Int) = (modifiers and Opcodes.ACC_PROTECTED) == Opcodes.ACC_PROTECTED
fun isStatic(modifiers: Int) = (modifiers and Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC
fun isFinal(modifiers: Int) = (modifiers and Opcodes.ACC_FINAL) == Opcodes.ACC_FINAL
fun isSuper(modifiers: Int) = (modifiers and Opcodes.ACC_SUPER) == Opcodes.ACC_SUPER
fun isSynchronized(modifiers: Int) = (modifiers and Opcodes.ACC_SYNCHRONIZED) == Opcodes.ACC_SYNCHRONIZED
fun isVolatile(modifiers: Int) = (modifiers and Opcodes.ACC_VOLATILE) == Opcodes.ACC_VOLATILE
fun isBridge(modifiers: Int) = (modifiers and Opcodes.ACC_BRIDGE) == Opcodes.ACC_BRIDGE
fun isVarargs(modifiers: Int) = (modifiers and Opcodes.ACC_VARARGS) == Opcodes.ACC_VARARGS
fun isTransient(modifiers: Int) = (modifiers and Opcodes.ACC_TRANSIENT) == Opcodes.ACC_TRANSIENT
fun isNative(modifiers: Int) = (modifiers and Opcodes.ACC_NATIVE) == Opcodes.ACC_NATIVE
fun isInterface(modifiers: Int) = (modifiers and Opcodes.ACC_INTERFACE) == Opcodes.ACC_INTERFACE
fun isAbstract(modifiers: Int) = (modifiers and Opcodes.ACC_ABSTRACT) == Opcodes.ACC_ABSTRACT
fun isStrict(modifiers: Int) = (modifiers and Opcodes.ACC_STRICT) == Opcodes.ACC_STRICT
fun isSynthetic(modifiers: Int) = (modifiers and Opcodes.ACC_SYNTHETIC) == Opcodes.ACC_SYNTHETIC
fun isAnnotation(modifiers: Int) = (modifiers and Opcodes.ACC_ANNOTATION) == Opcodes.ACC_ANNOTATION
fun isEnum(modifiers: Int) = (modifiers and Opcodes.ACC_ENUM) == Opcodes.ACC_ENUM