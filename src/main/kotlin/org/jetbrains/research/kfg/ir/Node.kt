package org.jetbrains.research.kfg.ir

import org.jetbrains.research.kfg.ClassManager
import org.objectweb.asm.Opcodes

abstract class Node(val cm: ClassManager, val name: String, modifiers: Int) {
    var modifiers: Int = modifiers
        protected set

    abstract val asmDesc: String

    var isPublic: Boolean
        get() = (modifiers and Opcodes.ACC_PUBLIC) == Opcodes.ACC_PUBLIC
        set(value) {
            modifiers = when {
                value -> modifiers or Opcodes.ACC_PUBLIC
                else -> modifiers and (Opcodes.ACC_PUBLIC).inv()
            }
        }

    var isPrivate: Boolean
        get() = (modifiers and Opcodes.ACC_PRIVATE) == Opcodes.ACC_PRIVATE
        set(value) {
            modifiers = when {
                value -> modifiers or Opcodes.ACC_PRIVATE
                else -> modifiers and (Opcodes.ACC_PRIVATE).inv()
            }
        }

    var isProtected: Boolean
        get() = (modifiers and Opcodes.ACC_PROTECTED) == Opcodes.ACC_PROTECTED
        set(value) {
            modifiers = when {
                value -> modifiers or Opcodes.ACC_PROTECTED
                else -> modifiers and (Opcodes.ACC_PROTECTED).inv()
            }
        }

    var isStatic: Boolean
        get() = (modifiers and Opcodes.ACC_STATIC) == Opcodes.ACC_STATIC
        set(value) {
            modifiers = when {
                value -> modifiers or Opcodes.ACC_STATIC
                else -> modifiers and (Opcodes.ACC_STATIC).inv()
            }
        }

    var isFinal: Boolean
        get() = (modifiers and Opcodes.ACC_FINAL) == Opcodes.ACC_FINAL
        set(value) {
            modifiers = when {
                value -> modifiers or Opcodes.ACC_FINAL
                else -> modifiers and (Opcodes.ACC_FINAL).inv()
            }
        }

    var isSuper: Boolean
        get() = (modifiers and Opcodes.ACC_SUPER) == Opcodes.ACC_SUPER
        set(value) {
            modifiers = when {
                value -> modifiers or Opcodes.ACC_SUPER
                else -> modifiers and (Opcodes.ACC_SUPER).inv()
            }
        }

    var isSynchronized: Boolean
        get() = (modifiers and Opcodes.ACC_SYNCHRONIZED) == Opcodes.ACC_SYNCHRONIZED
        set(value) {
            modifiers = when {
                value -> modifiers or Opcodes.ACC_SYNCHRONIZED
                else -> modifiers and (Opcodes.ACC_SYNCHRONIZED).inv()
            }
        }

    var isVolatile: Boolean
        get() = (modifiers and Opcodes.ACC_VOLATILE) == Opcodes.ACC_VOLATILE
        set(value) {
            modifiers = when {
                value -> modifiers or Opcodes.ACC_VOLATILE
                else -> modifiers and (Opcodes.ACC_VOLATILE).inv()
            }
        }

    var isBridge: Boolean
        get() = (modifiers and Opcodes.ACC_BRIDGE) == Opcodes.ACC_BRIDGE
        set(value) {
            modifiers = when {
                value -> modifiers or Opcodes.ACC_BRIDGE
                else -> modifiers and (Opcodes.ACC_BRIDGE).inv()
            }
        }

    var isVarargs: Boolean
        get() = (modifiers and Opcodes.ACC_VARARGS) == Opcodes.ACC_VARARGS
        set(value) {
            modifiers = when {
                value -> modifiers or Opcodes.ACC_VARARGS
                else -> modifiers and (Opcodes.ACC_VARARGS).inv()
            }
        }

    var isTransient: Boolean
        get() = (modifiers and Opcodes.ACC_TRANSIENT) == Opcodes.ACC_TRANSIENT
        set(value) {
            modifiers = when {
                value -> modifiers or Opcodes.ACC_TRANSIENT
                else -> modifiers and (Opcodes.ACC_TRANSIENT).inv()
            }
        }

    var isNative: Boolean
        get() = (modifiers and Opcodes.ACC_NATIVE) == Opcodes.ACC_NATIVE
        set(value) {
            modifiers = when {
                value -> modifiers or Opcodes.ACC_NATIVE
                else -> modifiers and (Opcodes.ACC_NATIVE).inv()
            }
        }

    var isInterface: Boolean
        get() = (modifiers and Opcodes.ACC_INTERFACE) == Opcodes.ACC_INTERFACE
        set(value) {
            modifiers = when {
                value -> modifiers or Opcodes.ACC_INTERFACE
                else -> modifiers and (Opcodes.ACC_INTERFACE).inv()
            }
        }

    var isAbstract: Boolean
        get() = (modifiers and Opcodes.ACC_ABSTRACT) == Opcodes.ACC_ABSTRACT
        set(value) {
            modifiers = when {
                value -> modifiers or Opcodes.ACC_ABSTRACT
                else -> modifiers and (Opcodes.ACC_ABSTRACT).inv()
            }
        }

    var isStrict: Boolean
        get() = (modifiers and Opcodes.ACC_STRICT) == Opcodes.ACC_STRICT
        set(value) {
            modifiers = when {
                value -> modifiers or Opcodes.ACC_STRICT
                else -> modifiers and (Opcodes.ACC_STRICT).inv()
            }
        }

    var isSynthetic: Boolean
        get() = (modifiers and Opcodes.ACC_SYNTHETIC) == Opcodes.ACC_SYNTHETIC
        set(value) {
            modifiers = when {
                value -> modifiers or Opcodes.ACC_SYNTHETIC
                else -> modifiers and (Opcodes.ACC_SYNTHETIC).inv()
            }
        }

    var isAnnotation: Boolean
        get() = (modifiers and Opcodes.ACC_ANNOTATION) == Opcodes.ACC_ANNOTATION
        set(value) {
            modifiers = when {
                value -> modifiers or Opcodes.ACC_ANNOTATION
                else -> modifiers and (Opcodes.ACC_ANNOTATION).inv()
            }
        }

    var isEnum: Boolean
        get() = (modifiers and Opcodes.ACC_ENUM) == Opcodes.ACC_ENUM
        set(value) {
            modifiers = when {
                value -> modifiers or Opcodes.ACC_ENUM
                else -> modifiers and (Opcodes.ACC_ENUM).inv()
            }
        }
}