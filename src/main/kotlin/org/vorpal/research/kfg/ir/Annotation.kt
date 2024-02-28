package org.vorpal.research.kfg.ir

import org.objectweb.asm.TypePath
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.TypeAnnotationNode
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.type.Type
import org.vorpal.research.kfg.type.parseDescOrNull
import org.vorpal.research.kfg.util.TypeHolder
import org.vorpal.research.kfg.util.getAsKfgType
import org.vorpal.research.kthelper.assert.unreachable
import org.vorpal.research.kthelper.logging.log
import org.objectweb.asm.Type as AsmType

sealed interface AnnotationConstant {
    companion object {
        internal fun parse(cm: ClassManager, value: Any): AnnotationConstant = when (value) {
            is String -> StringAnnotationConstant(value)
            is Boolean -> BoolAnnotationConstant(value)
            is Char -> CharAnnotationConstant(value)
            is Number -> NumberAnnotationConstant(value)
            is Array<*> -> EnumAnnotationConstant(parseDescOrNull(cm.type, value[0] as String)!!, value[1] as String)
            is List<*> -> ArrayAnnotationConstant(value.map { parse(cm, it!!) })
            is AsmType -> TypeAnnotationConstant(value.getAsKfgType(cm))
            is AnnotationNode -> NestedAnnotationConstant(AnnotationBase.parseAnnotation(cm, value, visible = true))
            else -> unreachable { log.error("Unknown annotation constant type: $value") }
        }

        internal fun toAnnotationValue(constant: AnnotationConstant): Any = when (constant) {
            is BoolAnnotationConstant -> constant.boolean
            is CharAnnotationConstant -> constant.char
            is NumberAnnotationConstant<*> -> constant.number
            is StringAnnotationConstant -> constant.string
            is EnumAnnotationConstant -> arrayOf(constant.type.asmDesc, constant.name)
            is NestedAnnotationConstant -> AnnotationBase.toAnnotationNode(constant.annotation)
            is ArrayAnnotationConstant -> constant.values.map { toAnnotationValue(it) }
            is TypeAnnotationConstant -> constant.typeHolder.toAsmType()
        }
    }
}

data class StringAnnotationConstant(val string: String) : AnnotationConstant
data class BoolAnnotationConstant(val boolean: Boolean) : AnnotationConstant
data class CharAnnotationConstant(val char: Char) : AnnotationConstant
data class NumberAnnotationConstant<T : Number>(val number: T) : AnnotationConstant

data class EnumAnnotationConstant(val type: Type, val name: String) : AnnotationConstant
data class ArrayAnnotationConstant(val values: List<AnnotationConstant>) : AnnotationConstant
data class TypeAnnotationConstant(val typeHolder: TypeHolder) : AnnotationConstant
data class NestedAnnotationConstant(val annotation: Annotation) : AnnotationConstant

sealed interface AnnotationBase {
    val visible: Boolean
    val type: Type
    val values: Map<String, AnnotationConstant>

    companion object {
        internal fun parseAnnotation(cm: ClassManager, node: AnnotationNode, visible: Boolean): Annotation =
            parse(cm, node, visible) as Annotation

        internal fun parseTypeAnnotation(cm: ClassManager, node: TypeAnnotationNode, visible: Boolean): TypeAnnotation =
            parse(cm, node, visible) as TypeAnnotation

        internal fun parse(cm: ClassManager, node: AnnotationNode, visible: Boolean): AnnotationBase {
            val type = parseDescOrNull(cm.type, node.desc)!!
            val values = buildMap {
                for (index in 0 until node.values.orEmpty().size step 2) {
                    this[node.values[index] as String] = AnnotationConstant.parse(cm, node.values[index + 1])
                }
            }
            return when (node) {
                is TypeAnnotationNode -> TypeAnnotation(
                    visible,
                    type,
                    values,
                    node.typeRef,
                    node.typePath
                )

                else -> Annotation(visible, type, values)
            }
        }

        internal fun toAnnotationNode(annotation: Annotation): AnnotationNode {
            return AnnotationNode(annotation.type.asmDesc).also {
                val list = mutableListOf<Any>()
                for ((key, value) in annotation.values) {
                    list += key
                    list += AnnotationConstant.toAnnotationValue(value)
                }
                it.values = list
            }
        }

        internal fun toAnnotationNode(annotation: TypeAnnotation): TypeAnnotationNode {
            return TypeAnnotationNode(annotation.typeRef, annotation.typePath, annotation.type.asmDesc).also {
                val list = mutableListOf<Any>()
                for ((key, value) in annotation.values) {
                    list += key
                    list += AnnotationConstant.toAnnotationValue(value)
                }
                it.values = list
            }
        }
    }
}

data class Annotation(
    override val visible: Boolean,
    override val type: Type,
    override val values: Map<String, AnnotationConstant>
) : AnnotationBase

data class TypeAnnotation(
    override val visible: Boolean,
    override val type: Type,
    override val values: Map<String, AnnotationConstant>,
    val typeRef: Int,
    val typePath: TypePath?
) : AnnotationBase
