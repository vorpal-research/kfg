package org.jetbrains.research.kfg.builder.cfg.impl

import com.abdullin.kthelper.assert.unreachable
import com.abdullin.kthelper.logging.log
import org.jetbrains.research.kfg.ir.Method
import org.jetbrains.research.kfg.ir.value.Value
import org.jetbrains.research.kfg.type.Type
import org.jetbrains.research.kfg.type.TypeFactory
import org.jetbrains.research.kfg.type.parseDesc
import org.objectweb.asm.tree.AbstractInsnNode
import org.objectweb.asm.tree.FrameNode
import org.objectweb.asm.tree.LabelNode
import org.objectweb.asm.tree.TypeInsnNode
import java.util.*

private object TopType : Type {
    override val name: String
        get() = "TOP"
    override val asmDesc: String
        get() = "T"
    override val isPrimary: Boolean
        get() = false
    override val bitsize: Int
        get() = Type.WORD

    override fun isSubtypeOf(other: Type) = false
}

private object UninitializedThisType : Type {
    override val name: String
        get() = "this"
    override val asmDesc: String
        get() = "U"
    override val isPrimary: Boolean
        get() = false
    override val bitsize: Int
        get() = Type.WORD

    override fun isSubtypeOf(other: Type) = false
}

private fun parsePrimitiveType(tf: TypeFactory, opcode: Int) = when (opcode) {
    0 -> TopType
    1 -> tf.intType
    2 -> tf.floatType
    3 -> tf.doubleType
    4 -> tf.longType
    5 -> tf.nullType
    6 -> UninitializedThisType
    else -> unreachable { log.error("Unknown opcode in primitive type parsing: $opcode") }
}

fun parseFrameDesc(tf: TypeFactory, desc: String): Type = when (desc[0]) {
    'V' -> tf.voidType
    'Z' -> tf.boolType
    'B' -> tf.byteType
    'C' -> tf.charType
    'S' -> tf.shortType
    'I' -> tf.intType
    'J' -> tf.longType
    'F' -> tf.floatType
    'D' -> tf.doubleType
    '[' -> tf.getArrayType(parseDesc(tf, desc.drop(1)))
    else -> tf.getRefType(desc)
}

private fun parseType(types: TypeFactory, any: Any): Type = when (any) {
    is String -> parseFrameDesc(types, any)
    is Int -> parsePrimitiveType(types, any)
    is LabelNode -> {
        val newNode: TypeInsnNode = any.run {
            var cur: AbstractInsnNode = this
            var typeInsnNode: TypeInsnNode?
            do {
                typeInsnNode = cur.next as? TypeInsnNode
                cur = cur.next
            } while (typeInsnNode == null)
            typeInsnNode
        }
        parseFrameDesc(types, newNode.desc)
    }
    else -> unreachable { log.error("Unexpected local type $any") }
}

private fun List<*>?.parseLocals(types: TypeFactory): SortedMap<Int, Type> {
    if (this == null) return sortedMapOf()
    val result = mutableMapOf<Int, Type>()
    var index = 0
    for (any in this) {
        val type = parseType(types, any!!)
        result[index] = type
        when {
            type.isDWord -> index += 2
            else -> ++index
        }
    }
    return result.toSortedMap()
}

private fun List<*>?.parseStack(types: TypeFactory): SortedMap<Int, Type> {
    if (this == null) return sortedMapOf()
    val result = mutableMapOf<Int, Type>()
    for ((index, any) in this.withIndex()) {
        val type = parseType(types, any!!)
        result[index] = type
    }
    return result.toSortedMap()
}

internal data class FrameState(
        val types: TypeFactory,
        val method: Method,
        private val innerLocal: SortedMap<Int, Type>,
        private val innerStack: SortedMap<Int, Type>) {
    val local: SortedMap<Int, Type> get() = innerLocal.filtered
    val stack: SortedMap<Int, Type> get() = innerStack.filtered

    private val SortedMap<Int, Type>.filtered: SortedMap<Int, Type>
        get() = this.filterValues { it !is TopType }.mapValues {
            when (it.value) {
                is UninitializedThisType -> types.getRefType(method.`class`)
                else -> it.value
            }
        }.toSortedMap()

    companion object {
        fun parse(types: TypeFactory, method: Method, inst: FrameNode) = FrameState(
                types,
                method,
                inst.local.parseLocals(types),
                inst.stack.parseStack(types)
        )

        fun parse(types: TypeFactory, method: Method, locals: Map<Int, Value>, stack: List<Value>) = FrameState(
                types,
                method,
                locals.mapValues { it.value.type }.toSortedMap(),
                stack.withIndex().map { it.index to it.value.type }.toMap().toSortedMap()
        )
    }

    fun appendFrame(inst: FrameNode): FrameState {
        val maxKey = this.innerLocal.keys.max() ?: -1
        val lastType = innerLocal[maxKey]
        val insertKey = when {
            lastType == null -> 0
            lastType.isDWord -> maxKey + 2
            else -> maxKey + 1
        }
        val appendedLocals = inst.local.parseLocals(types)
        val newLocals = this.innerLocal.toMutableMap()
        for ((index, type) in appendedLocals) {
            newLocals[insertKey + index] = type
        }
        return copy(innerLocal = newLocals.toSortedMap(), innerStack = sortedMapOf())
    }

    fun dropFrame(inst: FrameNode): FrameState {
        val newLocals = this.innerLocal.toList().dropLast(inst.local.size).toMap().toSortedMap()
        return copy(innerLocal = newLocals, innerStack = sortedMapOf())
    }

    fun copy(): FrameState = this.copy(innerStack = sortedMapOf())

    fun copy1(inst: FrameNode): FrameState = this.copy(innerStack = inst.stack.parseStack(types))
}