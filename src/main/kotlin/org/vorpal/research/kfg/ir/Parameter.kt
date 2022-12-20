package org.vorpal.research.kfg.ir

import org.objectweb.asm.tree.AnnotationNode
import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.type.Type

open class Parameter(
    cm: ClassManager,
    val index: Int,
    name: String,
    val type: Type,
    modifiers: Modifiers,
    val annotations: List<MethodParameterAnnotation>
) : Node(cm, name, modifiers) {
    override val asmDesc = type.asmDesc

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Parameter) return false

        if (index != other.index) return false
        if (type != other.type) return false
        if (asmDesc != other.asmDesc) return false
        if (annotations != other.annotations) return false

        return true
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + type.hashCode()
        result = 31 * result + asmDesc.hashCode()
        return result
    }
}

class StubParameter(
    cm: ClassManager,
    index: Int,
    type: Type,
    modifiers: Modifiers,
    annotations: List<MethodParameterAnnotation>
) : Parameter(cm, index, name = NAME, type, modifiers, annotations) {
    companion object {
        const val NAME = "stub"
    }
}

/**
 * @param arguments: argument is a pair of name to value where value could be one of java's primitive type wrapper
 * (Integer, Long, ...), String, Reference, Enum or Array
 */
data class MethodParameterAnnotation(
    val type: Type,
    val arguments: Map<String, Any>
) {
    companion object {
        fun get(annotationNode: AnnotationNode, cm: ClassManager): MethodParameterAnnotation {
            val fullName = getAnnotationFullName(annotationNode.desc)
            val type = cm[fullName].asType

            val keys = annotationNode.values.orEmpty()
                .filterIndexed { index, _ -> index.mod(2) == 0 }
                .filterIsInstance<String>()
            val values = annotationNode.values.orEmpty()
                .filterIndexed { index, _ -> index.mod(2) == 1 }

            return MethodParameterAnnotation(type, mapOf(*(keys zip values).toTypedArray()))
        }

        private fun getAnnotationFullName(desc: String): String {
            return desc.removePrefix("L").removeSuffix(";")
        }
    }
}
