package org.vorpal.research.kfg.ir

import org.vorpal.research.kfg.ClassManager
import org.vorpal.research.kfg.type.Type

class Parameter(
    cm: ClassManager,
    val index: Int,
    name: String,
    val type: Type,
    modifiers: Modifiers,
    annotations: Set<Annotation>
) : Node(cm, name, modifiers) {
    companion object {
        const val STUB_NAME = "STUB"
    }

    override val asmDesc = type.asmDesc
    override val innerAnnotations = annotations.toMutableSet()
    override val innerTypeAnnotations = mutableSetOf<TypeAnnotation>()

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

    public override fun addAnnotation(annotation: Annotation) {
        super.addAnnotation(annotation)
    }

    public override fun removeAnnotation(annotation: Annotation) {
        super.removeAnnotation(annotation)
    }
}

